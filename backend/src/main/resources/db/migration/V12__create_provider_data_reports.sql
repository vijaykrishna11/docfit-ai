-- Directory-data correction reports (CLAUDE.md "Data Correction Reporting"). These are review
-- signals only -- nothing in the application reads this table to alter provider/location data
-- automatically (CLAUDE.md "Reports are review signals only"). user_id is nullable: anonymous
-- submission is allowed (rate-limited at the application layer), matching the product decision
-- documented in docs/directory-corrections.md.
CREATE TABLE provider_data_report (
    id                  BIGSERIAL PRIMARY KEY,
    provider_id         BIGINT      NOT NULL REFERENCES provider (id),
    provider_location_id BIGINT     REFERENCES provider_location (id),
    user_id             BIGINT      REFERENCES app_user (id),
    report_type         VARCHAR(50) NOT NULL,
    comment              VARCHAR(1000),
    status               VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'REVIEWED', 'RESOLVED', 'DISMISSED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_data_report_provider_id ON provider_data_report (provider_id);
CREATE INDEX idx_provider_data_report_status ON provider_data_report (status);
