-- Import provenance/history (CLAUDE.md 23-24). Deliberately minimal -- counts and status only,
-- never raw source payloads.
CREATE TABLE data_import (
    id                  BIGSERIAL PRIMARY KEY,
    source               VARCHAR(50)  NOT NULL,
    started_at           TIMESTAMPTZ  NOT NULL,
    completed_at         TIMESTAMPTZ,
    status               VARCHAR(20)  NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL')),
    records_read         INTEGER      NOT NULL DEFAULT 0,
    providers_created     INTEGER      NOT NULL DEFAULT 0,
    providers_updated     INTEGER      NOT NULL DEFAULT 0,
    locations_created     INTEGER      NOT NULL DEFAULT 0,
    locations_updated     INTEGER      NOT NULL DEFAULT 0,
    records_failed        INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_data_import_source ON data_import (source);
