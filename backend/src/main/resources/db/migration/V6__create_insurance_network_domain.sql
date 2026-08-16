-- Insurance network intelligence domain model. See docs/insurance-network-architecture.md for
-- the full design rationale. The existing insurance_carrier table and /api/insurance-carriers
-- endpoint are left untouched (legacy, informational-only; nothing here removes them).

CREATE TABLE payer (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    website    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE network_source (
    id                   BIGSERIAL PRIMARY KEY,
    payer_id             BIGINT       NOT NULL REFERENCES payer (id),
    source_type          VARCHAR(30)  NOT NULL
        CHECK (source_type IN ('FHIR_API', 'JSON_API', 'MACHINE_READABLE_FILE', 'MANUAL_DEMO_REFERENCE')),
    name                 VARCHAR(200) NOT NULL,
    base_url_reference   VARCHAR(500),
    format               VARCHAR(30),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    last_successful_check TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_network_source_payer_id ON network_source (payer_id);

CREATE TABLE insurance_plan (
    id                       BIGSERIAL PRIMARY KEY,
    payer_id                 BIGINT       NOT NULL REFERENCES payer (id),
    plan_name                VARCHAR(200) NOT NULL,
    plan_type                VARCHAR(20)  NOT NULL DEFAULT 'OTHER'
        CHECK (plan_type IN ('HMO', 'PPO', 'EPO', 'POS', 'OTHER')),
    state                    VARCHAR(2),
    external_plan_identifier VARCHAR(100),
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    source_id                BIGINT REFERENCES network_source (id),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_insurance_plan_payer_id ON insurance_plan (payer_id);

CREATE TABLE insurance_network (
    id                          BIGSERIAL PRIMARY KEY,
    payer_id                    BIGINT       NOT NULL REFERENCES payer (id),
    network_name                VARCHAR(200) NOT NULL,
    external_network_identifier VARCHAR(100),
    state                       VARCHAR(2),
    active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_insurance_network_payer_id ON insurance_network (payer_id);

-- A plan may participate in one or more networks; modeled as a real join table rather than a
-- collapsed string, since a national plan can carry a state-specific network overlay.
CREATE TABLE plan_network (
    plan_id    BIGINT NOT NULL REFERENCES insurance_plan (id),
    network_id BIGINT NOT NULL REFERENCES insurance_network (id),
    PRIMARY KEY (plan_id, network_id)
);

-- The critical model: directory evidence that a provider participates in a network, optionally
-- scoped to a specific plan and/or practice location. Never a coverage guarantee -- see
-- docs/insurance-network-architecture.md and CLAUDE.md's insurance-language rules.
CREATE TABLE provider_network_evidence (
    id                         BIGSERIAL PRIMARY KEY,
    provider_id                BIGINT      NOT NULL REFERENCES provider (id),
    insurance_network_id       BIGINT      NOT NULL REFERENCES insurance_network (id),
    insurance_plan_id          BIGINT REFERENCES insurance_plan (id),
    status                     VARCHAR(30) NOT NULL
        CHECK (status IN ('EVIDENCE_FOUND', 'NO_EVIDENCE_FOUND', 'SOURCE_UNAVAILABLE', 'MATCH_AMBIGUOUS', 'NOT_CHECKED')),
    source_id                  BIGINT      NOT NULL REFERENCES network_source (id),
    source_provider_identifier VARCHAR(50),
    source_network_identifier  VARCHAR(100),
    -- Snapshot of the matched practice location, not a provider_location FK: DocFit AI's
    -- provider table stores a single address per NPI today (see
    -- docs/insurance-network-research.md, "Location model"), so this is the minimal way to keep
    -- evidence genuinely location-specific without inventing a multi-location schema nothing
    -- else uses yet.
    matched_address_line1      VARCHAR(200),
    matched_city                VARCHAR(100),
    matched_state_code          VARCHAR(2),
    matched_postal_code         VARCHAR(10),
    match_method                VARCHAR(30) NOT NULL
        CHECK (match_method IN ('NPI_EXACT', 'NPI_AND_LOCATION', 'NPI_AND_POSTAL_CODE', 'ORGANIZATION_NPI', 'AMBIGUOUS')),
    first_seen_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    checked_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_last_updated_at       TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pne_provider_id ON provider_network_evidence (provider_id);
CREATE INDEX idx_pne_network_id ON provider_network_evidence (insurance_network_id);
CREATE INDEX idx_pne_plan_id ON provider_network_evidence (insurance_plan_id);

-- Dedup on repeated imports (CLAUDE.md 27): two partial unique indexes because Postgres treats
-- NULL as distinct in a plain unique constraint, which would defeat dedup for network-only
-- (no specific plan) evidence rows.
CREATE UNIQUE INDEX uq_pne_with_plan ON provider_network_evidence (provider_id, insurance_network_id, insurance_plan_id, source_id)
    WHERE insurance_plan_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pne_without_plan ON provider_network_evidence (provider_id, insurance_network_id, source_id)
    WHERE insurance_plan_id IS NULL;

-- Migrate the existing demo carrier names into `payer` as "known to DocFit, no integration" --
-- see docs/insurance-network-research.md (carrier-seed investigation). The legacy
-- insurance_carrier table and /api/insurance-carriers endpoint are left exactly as they were.
INSERT INTO payer (code, name)
SELECT upper(regexp_replace(name, '[^A-Za-z0-9]+', '_', 'g')), name
FROM insurance_carrier
ORDER BY id;
