# Web deployment checklist

Exact pre-deployment checklist and first-deployment procedure for DocFit AI Web Beta v0.1.0-beta.1.
Companion docs: `docs/production-deployment-plan.md` (architecture/requirements detail),
`docs/web-beta-release-notes.md` (what's shipping), `docs/operations-runbook.md` (day-2 ops).

## Pre-deployment checklist

- [x] Git release clean -- `release/web-beta-v1` created from a fully verified, linear-ancestor
      state; working tree clean; no unpushed commits.
- [x] Backend tests green -- 207/207.
- [x] Frontend tests green -- typecheck/lint/test/build all clean.
- [x] E2E green -- 25/25, run twice, no flakiness.
- [x] `npm audit` -- 0 vulnerabilities.
- [x] Docker image builds and actually runs (migrations, prod-config refusals, real traffic all
      verified against a disposable Postgres container).
- [ ] **Cookie/CORS topology decided** -- see "Critical" section in
      `docs/production-deployment-plan.md`. MUST be resolved before choosing a hosting provider,
      not after.
- [ ] PostgreSQL provisioned (managed instance, real credentials, not `localhost:5433`).
- [ ] `JWT_SECRET` generated (real, random, 256+ bits -- e.g. `openssl rand -base64 48`).
- [ ] `CORS_ALLOWED_ORIGINS` known (the real deployed frontend origin, decided together with the
      cookie topology item above).
- [ ] Secure cookie config confirmed (`AUTH_COOKIE_SECURE=true`, the `prod` profile default --
      only needs explicit setting if a specific deployment overrides it).
- [ ] Synthetic insurance OFF (`DOCFIT_SYNTHETIC_INSURANCE_ENABLED` unset -- default is already
      off; the `prod` profile refuses to start if this is somehow `true`).
- [ ] Provider bootstrap method decided (geography import + bounded NPPES import -- see "Production
      database bootstrap plan," below).
- [ ] Frontend `VITE_API_BASE_URL` known (the real deployed backend origin).
- [ ] Health endpoint reachable post-deploy (`GET /actuator/health`).
- [ ] SPA rewrite configured at the hosting layer (see below).
- [ ] Smoke tests run against the real deployed environment (see "Post-deploy smoke tests," below).
- [ ] Backup plan confirmed (managed Postgres provider's own automated backups, at minimum).
- [ ] Rollback plan confirmed (see below).

## Required backend environment variables

| Variable | Required | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `prod` |
| `JWT_SECRET` | Yes | Real, random, 256+ bits |
| `CORS_ALLOWED_ORIGINS` | Yes | Real frontend origin, never localhost |
| `SPRING_DATASOURCE_URL` | Yes | `jdbc:postgresql://<host>:<port>/<db>` -- transform the managed provider's connection string into this exact JDBC form; do not paste a `postgres://` URI directly |
| `POSTGRES_PASSWORD` (or the datasource password property directly) | Yes | Real DB password, never `changeme` |
| `POSTGRES_USER` | Yes (unless baked into `SPRING_DATASOURCE_URL`) | Real DB user |
| `PORT` | Usually automatic | Most PaaS platforms set this themselves |
| `AUTH_COOKIE_SECURE` | No | Defaults to `true` under `prod` |
| `DOCFIT_SYNTHETIC_INSURANCE_ENABLED` | No | Must stay unset/`false` |
| `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED` / `DOCFIT_GEOGRAPHY_IMPORT_ENABLED` / `DOCFIT_GEOCODE_ENABLED` | No | Must stay unset/`false` on every normal boot -- these are one-shot operator actions (see below), not always-on flags |

## Required frontend environment variables

| Variable | Required | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | Yes | The real deployed backend origin, baked in at build time (Vite env vars are compile-time, not runtime) |

## SPA routing requirement

The frontend is a client-side-routed SPA (React Router) with routes including `/providers/:id`,
`/signin`, `/register`, `/saved`, `/saved-searches`, `/shortlists`, `/shortlists/:id`, `/navigator`,
`/account`, `/compare`, `/share/providers`. The hosting layer **must** rewrite all non-asset paths
to `index.html` -- verify specifically with a hard refresh (not just client-side navigation) on a
deep route like `/providers/123`; it must not 404.

## Production database bootstrap plan (first deployment only)

1. Provision the managed PostgreSQL instance.
2. Configure the backend's environment variables (above).
3. Start the backend (default profile boot, or `prod` once the cookie/CORS topology is decided) --
   Flyway applies all 19 migrations automatically on first startup. Verified this phase against a
   genuinely empty database.
4. Load reference geography: run the geography importer once
   (`DOCFIT_GEOGRAPHY_IMPORT_ENABLED=true` for one boot, then unset it) -- loads the real,
   bundled, source-verified 295-ZIP LA County reference set.
5. Execute a bounded provider bootstrap import: `SPRING_PROFILES_ACTIVE=import` with
   `DOCFIT_NPPES_IMPORT_ZIP_CODES` set to a deliberately-chosen ZIP list (see
   `docs/la-county-provider-import.md` for the methodology used to choose the original 30). Do
   **not** run this unscoped against all 295 ZIPs on a first deployment without first re-confirming
   the request/runtime budget in `docs/la-county-provider-import.md`.
6. Run the data quality report (`SPRING_PROFILES_ACTIVE=quality-report`) and review any
   `ERROR`-severity findings.
7. Inspect `GET /api/discovery/coverage` to confirm real counts before enabling the frontend.
8. Enable/point the frontend at the backend.

**Production will start with zero providers until step 5 is deliberately run** -- confirmed
directly this phase. Nothing auto-imports on normal startup.

## Do NOT deploy a local database dump by default

Prefer the reproducible bootstrap above over uploading a database dump. A local dev database dump
would carry test/demo user accounts, saved searches, shortlists, and reminders that must never
reach production. If a seed/backup approach is ever considered instead, it must first be scrubbed
of all `app_user`/`refresh_token`/`saved_provider`/`saved_search`/`provider_shortlist`/
`user_provider_navigation`/`provider_verification_item`/`user_reminder`/`user_saved_plan` rows --
public provider/reference data (`provider`, `provider_location`, `provider_taxonomy`,
`zip_geography`, `specialty`, `npi_taxonomy`) is the only category safe to consider carrying over,
and even then the reproducible import above is preferred since it's independently verifiable.

## Post-deploy smoke tests

- `GET /actuator/health` -> `200`.
- Homepage loads.
- `GET /api/specialties` -> 19 real categories.
- A real provider search (e.g. Primary Care + a loaded ZIP) returns results.
- Provider detail page loads.
- Map/list view (if applicable) renders.
- Register a real test account.
- Login.
- **Confirm the refresh cookie actually round-trips** -- this is the one most likely to silently
  fail on a bad topology choice (see "Critical" cookie/CORS finding). Test by waiting past the
  short access-token TTL (or forcing a refresh) and confirming the session survives, not just that
  login initially succeeds.
- Save a provider.
- Create a shortlist.
- Visit the Navigator.
- Log out.
- `GET /api/discovery/coverage` -> real counts matching what was actually bootstrapped.

## Backup plan

Rely on the managed PostgreSQL provider's own automated backups as the first line of defense. The
disposable-container dump/restore rehearsal technique used throughout this project's development
(`docs/operations-runbook.md` "Backup/restore rehearsal") is a reasonable periodic manual check,
not a replacement for the provider's own backup service.

## Rollback plan

- **Application rollback**: redeploy the previous known-good image/build. The app is stateless
  (JWT access tokens, in-memory rate limiter) -- no in-memory state is lost across a redeploy.
- **Database rollback**: every Flyway migration in this codebase is additive (see
  `docs/operations-runbook.md` "Rollback plan"); there is no destructive migration to roll back
  from as of this release. Restore from the managed provider's backup if a rollback of data itself
  is ever needed.
- **Config rollback**: revert the specific changed environment variable(s) and restart;
  `ProductionSafetyValidator` refuses to start if the rollback itself produces an unsafe config.
- **Recovery tag**: `docfit-pre-web-beta-2026-08-17` marks the exact commit before this
  release-prep phase began, pushed to origin, for reference if ever needed.
