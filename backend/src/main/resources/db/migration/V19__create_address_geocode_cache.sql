-- Address-level geocode cache (CLAUDE.md "Geocoding Pipeline" -- "cache by normalized address hash
-- to avoid re-geocoding unchanged addresses"). Keyed by the same deterministic normalization
-- LocationNormalizer already uses for provider_location dedup, so an address that hasn't changed
-- never triggers a second real geocoder call.
CREATE TABLE address_geocode_cache (
    normalized_address  VARCHAR(512) PRIMARY KEY,
    match_status        VARCHAR(20)  NOT NULL CHECK (match_status IN ('MATCHED', 'NO_MATCH', 'FAILED')),
    latitude             NUMERIC(9,6),
    longitude            NUMERIC(9,6),
    matched_address      VARCHAR(300),
    -- Bounded -- never a raw stack trace or unbounded error payload (CLAUDE.md "bounded failure reason").
    failure_reason       VARCHAR(300),
    geocoded_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
