-- Care Navigator V4: administrative navigation status, a user-tracked (not DocFit-asserted)
-- verification checklist, lightweight in-app reminders, and an explicit opt-in saved insurance
-- plan. All four tables are user-owned data, deleted on account deletion (AuthService), and never
-- read as a source of truth about the provider itself (CLAUDE.md "User-Confirmed vs Source Data").

-- Administrative status a user assigns to a provider they're considering (never a clinical/quality
-- judgement -- CLAUDE.md "Navigation Status"). One row per (user, provider); independent of
-- provider_shortlist membership -- a provider can carry a status without belonging to any
-- shortlist, and vice versa.
CREATE TABLE user_provider_navigation (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id),
    provider_id BIGINT      NOT NULL REFERENCES provider (id),
    status      VARCHAR(30) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider_id)
);

CREATE INDEX idx_user_provider_navigation_user_id ON user_provider_navigation (user_id);

-- A user's own administrative "have I confirmed this yet" tracker. CONFIRMED_BY_USER means only
-- that the user says they confirmed it directly with the provider/insurer -- this never writes
-- back to or overrides provider/provider_location/network-evidence data (CLAUDE.md "User
-- Confirmation Semantics").
CREATE TABLE provider_verification_item (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES app_user (id),
    provider_id           BIGINT      NOT NULL REFERENCES provider (id),
    provider_location_id  BIGINT      REFERENCES provider_location (id),
    verification_type     VARCHAR(40) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    confirmed_at          TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider_id, verification_type)
);

CREATE INDEX idx_provider_verification_item_user_id ON provider_verification_item (user_id);

-- Lightweight, user-created, in-app-only reminders (CLAUDE.md "Follow-Up Reminder Architecture")
-- -- no push/SMS/email integration this phase. due_at is stored in UTC; the frontend renders it in
-- the browser's local timezone. provider_id/shortlist_id are both optional and independent -- a
-- reminder may reference neither (a general reminder), either, but is not required to reference
-- both.
CREATE TABLE user_reminder (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id),
    provider_id   BIGINT       REFERENCES provider (id),
    shortlist_id  BIGINT       REFERENCES provider_shortlist (id),
    title         VARCHAR(200) NOT NULL,
    due_at        TIMESTAMPTZ  NOT NULL,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_reminder_user_id ON user_reminder (user_id);

-- Explicit opt-in saved plan: a reference to DocFit's own public payer/plan record only. Never
-- collects member ID / policy number / group number / DOB / SSN (CLAUDE.md "Do Not Store Member
-- Information"). One saved plan per user for MVP (CLAUDE.md "Saved Plan Model": prefer one active
-- default plan, keep simple) -- UNIQUE(user_id) enforces this at the database level, and the
-- service layer upserts rather than inserting a second row.
CREATE TABLE user_saved_plan (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) UNIQUE,
    insurance_plan_id BIGINT      NOT NULL REFERENCES insurance_plan (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
