-- Provider persistence for the NPPES import vertical slice. Minimal schema
-- for TODAY-MVP: real NPI Registry data, no scheduling/versioning tables.

CREATE TABLE provider (
    id                BIGSERIAL PRIMARY KEY,
    npi_number        VARCHAR(10)  NOT NULL UNIQUE,
    first_name        VARCHAR(100),
    last_name         VARCHAR(100),
    organization_name VARCHAR(200),
    phone             VARCHAR(20),
    address_line_1    VARCHAR(200) NOT NULL,
    address_line_2    VARCHAR(200),
    city              VARCHAR(100) NOT NULL,
    state_code        VARCHAR(2)   NOT NULL,
    postal_code       VARCHAR(10)  NOT NULL,
    latitude          NUMERIC(9,6),
    longitude         NUMERIC(9,6)
);

CREATE TABLE provider_taxonomy (
    provider_id      BIGINT      NOT NULL REFERENCES provider (id),
    taxonomy_code    VARCHAR(10) NOT NULL REFERENCES npi_taxonomy (taxonomy_code),
    primary_taxonomy BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (provider_id, taxonomy_code)
);

CREATE INDEX idx_provider_taxonomy_taxonomy_code ON provider_taxonomy (taxonomy_code);
CREATE INDEX idx_provider_postal_code ON provider (postal_code);
