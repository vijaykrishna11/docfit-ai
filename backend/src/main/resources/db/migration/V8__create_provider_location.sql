-- Provider Data Platform V2 -- STAGE A of a staged migration (see docs/provider-data-platform.md,
-- "Migration strategy"). This migration is purely additive/loosening: it creates
-- provider_location and backfills it from the existing single-address provider rows, adds
-- provider.entity_type, and loosens the now-deprecated address/phone/coordinate columns on
-- provider to nullable. It does NOT drop those legacy columns -- that is deliberately deferred to
-- a later "stage B" migration once the application code transition has run in practice, per
-- CLAUDE.md's explicit instruction to avoid a dangerous all-at-once migration.

CREATE TABLE provider_location (
    id                      BIGSERIAL PRIMARY KEY,
    provider_id             BIGINT       NOT NULL REFERENCES provider (id),
    address_purpose         VARCHAR(20)  NOT NULL DEFAULT 'LOCATION'
        CHECK (address_purpose IN ('LOCATION', 'MAILING')),
    address_line_1          VARCHAR(200) NOT NULL,
    address_line_2          VARCHAR(200),
    city                    VARCHAR(100) NOT NULL,
    state_code              VARCHAR(2)   NOT NULL,
    postal_code             VARCHAR(10)  NOT NULL,
    country_code            VARCHAR(2)   NOT NULL DEFAULT 'US',
    phone                   VARCHAR(20),
    fax                     VARCHAR(20),
    latitude                NUMERIC(9,6),
    longitude               NUMERIC(9,6),
    -- Truthful precision label -- see docs/provider-data-platform.md ("Location precision").
    -- Existing/demo coordinates come from zip_geography (a ZIP centroid lookup), never a real
    -- street-address geocode, so they are truthfully ZIP_CENTROID, not EXACT.
    coordinate_precision    VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN'
        CHECK (coordinate_precision IN ('EXACT', 'ADDRESS_GEOCODE', 'ZIP_CENTROID', 'CITY_CENTROID', 'UNKNOWN')),
    is_primary              BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Deterministic normalized-address dedup key (provider_id + normalized address fields), see
    -- docs/provider-data-platform.md ("Location uniqueness"). Computed identically in Java
    -- (LocationNormalizer) so a re-import of the same source address updates this row instead of
    -- creating a duplicate.
    normalized_key          VARCHAR(512) NOT NULL,
    source_last_updated_at  TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_provider_location_normalized ON provider_location (provider_id, normalized_key);
CREATE INDEX idx_provider_location_provider_id ON provider_location (provider_id);
CREATE INDEX idx_provider_location_postal_code ON provider_location (postal_code);

-- Backfill: one LOCATION row per existing provider, from its current (single) address. The
-- normalization here mirrors LocationNormalizer.normalizedKey(...) exactly (uppercase, trim,
-- collapse whitespace, strip periods/commas, base ZIP5) so a future re-import of the same NPPES
-- record for this provider recomputes the same key and updates this row instead of duplicating it.
INSERT INTO provider_location (
    provider_id, address_purpose, address_line_1, address_line_2, city, state_code, postal_code,
    phone, latitude, longitude, coordinate_precision, is_primary, normalized_key
)
SELECT
    id,
    'LOCATION',
    address_line_1,
    address_line_2,
    city,
    state_code,
    postal_code,
    phone,
    latitude,
    longitude,
    CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL THEN 'ZIP_CENTROID' ELSE 'UNKNOWN' END,
    TRUE,
    upper(regexp_replace(regexp_replace(trim(coalesce(address_line_1, '')), '[.,]', '', 'g'), '\s+', ' ', 'g'))
        || '|' ||
    upper(regexp_replace(regexp_replace(trim(coalesce(address_line_2, '')), '[.,]', '', 'g'), '\s+', ' ', 'g'))
        || '|' ||
    upper(regexp_replace(regexp_replace(trim(city), '[.,]', '', 'g'), '\s+', ' ', 'g'))
        || '|' ||
    upper(state_code)
        || '|' ||
    left(postal_code, 5)
FROM provider;

-- Provider becomes identity-focused (CLAUDE.md 3): entity type distinguishes individual (NPI-1)
-- from organization (NPI-2) providers, going forward populated from NPPES's own enumeration_type
-- rather than inferred. Existing rows are all individual providers (the importer has only ever
-- fetched NPI-1 so far), so backfilling from organization_name presence is accurate for today's
-- data, not a guess about future data.
ALTER TABLE provider ADD COLUMN entity_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL'
    CHECK (entity_type IN ('INDIVIDUAL', 'ORGANIZATION'));
UPDATE provider SET entity_type = 'ORGANIZATION' WHERE organization_name IS NOT NULL;
ALTER TABLE provider ALTER COLUMN entity_type DROP DEFAULT;

-- Loosen (never drop yet) the now-deprecated single-address columns so future provider rows --
-- which the application no longer populates directly, using provider_location instead -- don't
-- violate a NOT NULL constraint. Existing data is untouched.
ALTER TABLE provider ALTER COLUMN address_line_1 DROP NOT NULL;
ALTER TABLE provider ALTER COLUMN city DROP NOT NULL;
ALTER TABLE provider ALTER COLUMN state_code DROP NOT NULL;
ALTER TABLE provider ALTER COLUMN postal_code DROP NOT NULL;

-- Network evidence can now bind to a specific practice location when source data supports it
-- (CLAUDE.md 8-9). Left NULL when a location can't be deterministically resolved -- never guessed.
ALTER TABLE provider_network_evidence ADD COLUMN provider_location_id BIGINT REFERENCES provider_location (id);
CREATE INDEX idx_pne_location_id ON provider_network_evidence (provider_location_id);

-- Replace the old plan-only dedup indexes with ones that also key on provider_location_id, so
-- the same provider/network/plan/source combination at two different locations is correctly
-- treated as two distinct evidence observations, not a duplicate.
DROP INDEX uq_pne_with_plan;
DROP INDEX uq_pne_without_plan;

CREATE UNIQUE INDEX uq_pne_with_plan_with_location ON provider_network_evidence
    (provider_id, insurance_network_id, insurance_plan_id, source_id, provider_location_id)
    WHERE insurance_plan_id IS NOT NULL AND provider_location_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pne_with_plan_no_location ON provider_network_evidence
    (provider_id, insurance_network_id, insurance_plan_id, source_id)
    WHERE insurance_plan_id IS NOT NULL AND provider_location_id IS NULL;
CREATE UNIQUE INDEX uq_pne_no_plan_with_location ON provider_network_evidence
    (provider_id, insurance_network_id, source_id, provider_location_id)
    WHERE insurance_plan_id IS NULL AND provider_location_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pne_no_plan_no_location ON provider_network_evidence
    (provider_id, insurance_network_id, source_id)
    WHERE insurance_plan_id IS NULL AND provider_location_id IS NULL;
