-- TODAY-MVP reference schema.
-- Only the 5 tables approved for the one-day reference-data slice: no region /
-- region_zip_membership tables and no insurance_plan table yet.

CREATE TABLE specialty (
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(50)  NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE npi_taxonomy (
    taxonomy_code  VARCHAR(10)  PRIMARY KEY,
    classification VARCHAR(150) NOT NULL,
    specialization VARCHAR(150),
    display_name   VARCHAR(150) NOT NULL
);

CREATE TABLE specialty_taxonomy_mapping (
    specialty_id  BIGINT      NOT NULL REFERENCES specialty (id),
    taxonomy_code VARCHAR(10) NOT NULL REFERENCES npi_taxonomy (taxonomy_code),
    PRIMARY KEY (specialty_id, taxonomy_code)
);

CREATE TABLE zip_geography (
    zip_code   VARCHAR(5)    PRIMARY KEY,
    city       VARCHAR(100)  NOT NULL,
    state_code VARCHAR(2)    NOT NULL,
    latitude   NUMERIC(9,6)  NOT NULL,
    longitude  NUMERIC(9,6)  NOT NULL
);

CREATE TABLE insurance_carrier (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE
);
