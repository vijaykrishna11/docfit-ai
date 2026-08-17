-- Provider change detection (CLAUDE.md "Change Detection" / "Provider Change Event"): bounded,
-- meaningful-change-only event log, never a row per unchanged field and never a raw source
-- payload -- old_value/new_value are short, human-readable strings (a name, a phone number),
-- length-bounded at the database level.
CREATE TABLE provider_change_event (
    id                    BIGSERIAL PRIMARY KEY,
    provider_id           BIGINT      NOT NULL REFERENCES provider (id),
    change_type           VARCHAR(40) NOT NULL,
    provider_location_id  BIGINT      REFERENCES provider_location (id),
    old_value             VARCHAR(500),
    new_value             VARCHAR(500),
    source_import_id      BIGINT      REFERENCES data_import (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_change_event_provider_id ON provider_change_event (provider_id);
CREATE INDEX idx_provider_change_event_created_at ON provider_change_event (created_at);

-- Import scope metadata (CLAUDE.md "Complete vs Partial Import" / "Import Scope Metadata"):
-- provenance only. Deliberately NOT wired to any reconciliation/deactivation logic this phase --
-- no code path anywhere marks a provider or location inactive because an import didn't mention it
-- (CLAUDE.md "Partial Import Safety": "a bounded import ... MUST NOT mark every unseen provider
-- inactive"). Recording the scope now means a future reconciliation feature has the data it would
-- need to be built safely, without this phase having to implement the risky part.
ALTER TABLE data_import ADD COLUMN scope_type VARCHAR(30) NOT NULL DEFAULT 'PARTIAL';
ALTER TABLE data_import ADD COLUMN scope_description VARCHAR(300);
