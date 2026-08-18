# Web deployment checklist

Exact pre-deployment checklist and first-deployment procedure for DocFit AI Web Beta v0.1.0-beta.1.
Companion docs: `docs/production-deployment-plan.md` (architecture/requirements detail),
`docs/web-beta-release-notes.md` (what's shipping), `docs/operations-runbook.md` (day-2 ops).

## Pre-deployment checklist

- [x] Git release clean -- `release/web-beta-v1` created from a fully verified, linear-ancestor
      state; working tree clean; no unpushed commits.
- [x] Backend tests green -- 214/214 (includes new same-origin SPA routing regression tests).
- [x] Frontend tests green -- typecheck/lint/test/build all clean (47/47 unit tests, includes new
      same-origin API-base-URL regression tests).
- [x] E2E green against the real same-origin container (location/coverage/specialty test group,
      6/6; broader search-result E2E coverage needs a fuller data import than this rehearsal's
      small 2-ZIP sample -- see `docs/production-deployment-plan.md`).
- [x] `npm audit` -- 0 vulnerabilities.
- [x] Docker image builds and actually runs (migrations, prod-config refusals, real traffic,
      SPA routing, and static-asset serving all verified against a disposable Postgres container
      -- see `docs/production-deployment-plan.md` "Same-origin Docker + production rehearsal").
- [x] **Cookie/CORS topology decided** -- **same-origin, single Render Web Service** (root
      `Dockerfile`, Spring Boot serves both the API and the built SPA). Resolved without a custom
      domain -- see `docs/production-deployment-plan.md` "Deployment shape."
- [ ] PostgreSQL provisioned (managed instance, real credentials, not `localhost:5433`).
- [ ] `JWT_SECRET` generated (real, random, 256+ bits -- e.g. `openssl rand -base64 48`).
- [ ] `CORS_ALLOWED_ORIGINS` known -- same-origin deployment, so this is simply the service's own
      deployed URL (e.g. `https://docfit-ai.onrender.com`), known once the Render service name is
      chosen.
- [ ] Secure cookie config confirmed (`AUTH_COOKIE_SECURE=true`, the `prod` profile default --
      only needs explicit setting if a specific deployment overrides it).
- [ ] Synthetic insurance OFF (`DOCFIT_SYNTHETIC_INSURANCE_ENABLED` unset -- default is already
      off; the `prod` profile refuses to start if this is somehow `true`).
- [ ] Provider bootstrap method decided (geography import + bounded NPPES import -- see "Production
      database bootstrap plan," below).
- [ ] Health endpoint reachable post-deploy (`GET /actuator/health`).
- [ ] Smoke tests run against the real deployed environment (see "Post-deploy smoke tests," below).
- [ ] Backup plan confirmed (managed Postgres provider's own automated backups, at minimum).
- [ ] Rollback plan confirmed (see below).

## Required environment variables (one Render Web Service -- same-origin, no separate frontend host)

| Variable | Required | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `prod` |
| `JWT_SECRET` | Yes | Real, random, 256+ bits |
| `CORS_ALLOWED_ORIGINS` | Yes | The service's own deployed origin (e.g. `https://docfit-ai.onrender.com`) -- same-origin browser traffic doesn't strictly need CORS, but `ProductionSafetyValidator` still requires this set to a real, non-localhost value |
| `SPRING_DATASOURCE_URL` | Yes | `jdbc:postgresql://<host>:<port>/<db>` -- transform the managed provider's connection string into this exact JDBC form; do not paste a `postgres://` URI directly |
| `POSTGRES_PASSWORD` (or the datasource password property directly) | Yes | Real DB password, never `changeme` |
| `POSTGRES_USER` | Yes (unless baked into `SPRING_DATASOURCE_URL`) | Real DB user |
| `PORT` | Usually automatic | Render sets this itself; `server.port=${PORT:8080}` already respects it |
| `AUTH_COOKIE_SECURE` | No | Defaults to `true` under `prod` |
| `DOCFIT_SYNTHETIC_INSURANCE_ENABLED` | No | Must stay unset/`false` |
| `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED` / `DOCFIT_GEOGRAPHY_IMPORT_ENABLED` / `DOCFIT_GEOCODE_ENABLED` | No | Must stay unset/`false` on every normal boot -- these are one-shot operator actions (see below), not always-on flags |

**No separate frontend environment variables are needed for this deployment** -- `VITE_API_BASE_URL`
is deliberately left unset at build time so the frontend defaults to `window.location.origin`
(same-origin). It remains a supported override only if a future split-topology deployment (with a
custom domain) needs the frontend to call a different host.

## SPA routing

**No hosting-layer rewrite rule to configure** -- the frontend is embedded in and served by the
same Spring Boot process (`SpaWebConfig`), which handles serving `index.html` for client-side
routes (`/providers/:id`, `/signin`, `/register`, `/saved`, `/saved-searches`, `/shortlists`,
`/shortlists/:id`, `/navigator`, `/account`, `/compare`, `/share/providers`, `/locations`, and any
other/future frontend route) itself. Verified directly this phase with a hard refresh (not just
client-side navigation) on `/providers/123` against the real running container -- returns the real
SPA HTML, not a 404.

## Render Web Service settings (manual setup -- no `render.yaml` written this phase)

One Render **Web Service**, Docker runtime:

| Setting | Value |
|---|---|
| Root directory | Repository root (not `backend/`) |
| Dockerfile path | `Dockerfile` (the root one -- not `backend/Dockerfile`) |
| Docker build context | Repository root |
| Health check path | `/actuator/health` |
| Port | Auto-detected via `PORT` (Render sets it; the app reads it via `server.port=${PORT:8080}`) |

Plus one Render **PostgreSQL** instance, and the environment variables listed above set on the Web
Service. No separate Render Static Site is needed or used.

## Production database bootstrap plan (first deployment only)

1. Provision the managed PostgreSQL instance.
2. Configure the backend's environment variables (above).
3. Start the service (`prod` profile) -- Flyway applies all 19 migrations automatically on first
   startup. Verified this phase against a genuinely empty database, using the real root
   `Dockerfile` image (not just the Maven-run jar).
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
- **Confirm the refresh cookie actually round-trips.** Same-origin serving is exactly what makes
  this reliable now (no cross-site `SameSite=Lax` withholding) -- still worth confirming directly
  against the real deployment: wait past the short access-token TTL (or force a refresh) and
  confirm the session survives, not just that login initially succeeds.
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
