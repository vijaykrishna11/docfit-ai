-- Shortlists ("collections"): an optional organizational layer on top of the existing
-- saved_provider list (CLAUDE.md "Shortlists V2"). saved_provider remains the default,
-- one-click "Saved providers" behavior -- a shortlist is an additional, named grouping a
-- provider can optionally also belong to. Shortlist names are treated as private user data
-- (CLAUDE.md "Shortlist Domain Model": names may contain sensitive context like "Parents" or
-- "Near campus") -- never exposed outside the owning user's own authenticated requests.
CREATE TABLE provider_shortlist (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_shortlist_user_id ON provider_shortlist (user_id);

CREATE TABLE shortlist_provider (
    id           BIGSERIAL PRIMARY KEY,
    shortlist_id BIGINT      NOT NULL REFERENCES provider_shortlist (id) ON DELETE CASCADE,
    provider_id  BIGINT      NOT NULL REFERENCES provider (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shortlist_id, provider_id)
);

CREATE INDEX idx_shortlist_provider_shortlist_id ON shortlist_provider (shortlist_id);
