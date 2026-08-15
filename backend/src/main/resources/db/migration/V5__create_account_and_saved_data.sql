-- Account system for TODAY-MVP V2: minimal user fields only (no medical/health data).
-- Saved providers and saved searches are strictly opt-in (see application code) -- this
-- migration does not itself collect or infer anything about existing users.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Opaque refresh tokens are stored hashed and are individually revocable/rotated.
CREATE TABLE refresh_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);

-- Explicitly user-saved providers (a "shortlist"). Never auto-populated.
CREATE TABLE saved_provider (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id),
    provider_id BIGINT      NOT NULL REFERENCES provider (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider_id)
);

CREATE INDEX idx_saved_provider_user_id ON saved_provider (user_id);

-- Explicitly user-saved searches. Privacy-critical: DocFit AI never auto-saves search
-- history (see application code) -- a row here only exists because the user clicked
-- "Save this search".
CREATE TABLE saved_search (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    name           VARCHAR(100),
    specialty_code VARCHAR(50)  NOT NULL REFERENCES specialty (code),
    location_text  VARCHAR(200),
    latitude       NUMERIC(9,6),
    longitude      NUMERIC(9,6),
    radius         INTEGER      NOT NULL,
    sort           VARCHAR(20)  NOT NULL DEFAULT 'distance',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_saved_search_user_id ON saved_search (user_id);

-- Provenance: when DocFit AI imported each provider row. Existing rows are backfilled
-- with this migration's run time -- that is genuinely when DocFit's database first
-- recorded an import date for them, so it is labeled "Imported into DocFit AI on..."
-- in the UI, never "last updated" (which would misrepresent NPPES's own data).
ALTER TABLE provider ADD COLUMN imported_at TIMESTAMPTZ NOT NULL DEFAULT now();
