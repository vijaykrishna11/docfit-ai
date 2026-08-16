# Production deployment plan

What it actually takes to deploy DocFit AI, written from the real artifacts that exist in this
repository today -- not an aspirational target architecture. Companion to
`docs/operations-runbook.md` (day-2 operations) and `docs/threat-model.md`.

## Deployment shape

- **Backend**: a single Spring Boot jar, containerized via `backend/Dockerfile` (multi-stage,
  Java 21 JRE runtime, non-root user, no baked secrets -- all configuration via environment
  variables). Deploy anywhere that runs a container: a single VM with Docker, a managed container
  service, etc. No orchestration platform is assumed or required at this scale.
- **Frontend**: a static build (`npm run build` -> `frontend/dist/`) -- plain HTML/CSS/JS, no
  server-side rendering, no Node process needed at runtime. Deploy to any static host/CDN. **No
  frontend Dockerfile exists in this repository** -- if the chosen host requires a container (rather
  than direct static-file hosting), one would need to be added (a minimal `nginx`/`caddy` image
  serving `dist/`); not built this phase since it depends on the actual hosting choice.
- **Database**: external managed Postgres (or a self-run instance) -- not part of the application
  containers. `docker-compose.yml` in this repo starts Postgres only, for local development; there
  is no production-shaped compose file bundling backend+frontend+postgres together (see "Gaps"
  below).

## Required environment variables (backend, `prod` profile)

Enforced by `ProductionSafetyValidator` -- the app refuses to start under `SPRING_PROFILES_ACTIVE=prod`
without safe values for these:

| Variable | Requirement | Why |
|---|---|---|
| `JWT_SECRET` | Random, 256+ bits (32+ characters), not a known placeholder | Signs access tokens; a weak/guessable secret allows token forgery |
| `CORS_ALLOWED_ORIGINS` | The real frontend origin(s), never localhost/127.0.0.1 | Browsers enforce CORS based on this; wrong value either blocks the real frontend or (if wildcarded) opens the API to any origin |
| `AUTH_COOKIE_SECURE` | `true` (this is already the `prod` profile's default) | Refresh-token cookie must never be sent over plain HTTP |
| `POSTGRES_*` (host/port/db/user/password via `spring.datasource.*`) | Real production database credentials | -- |
| `DOCFIT_SYNTHETIC_INSURANCE_ENABLED` | Must be unset or `false` (already the default) | Synthetic network evidence must never seed in production |
| `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED` | Must be unset or `false` (already the default) | Bulk import must be operator-triggered, not always-on |

Optional but recommended:

| Variable | Purpose |
|---|---|
| `ACCESS_TOKEN_TTL_MINUTES` / `REFRESH_TOKEN_TTL_DAYS` | Session lifetime tuning (defaults: 15 min / 30 days) |
| `AUTH_RATE_LIMIT_MAX_ATTEMPTS` / `AUTH_RATE_LIMIT_WINDOW_MINUTES` | Login/register throttling tuning (defaults: 10 attempts / 5 min) |
| `FHIR_PLAN_NET_BASE_URL` | Only if a specific, vetted payer FHIR Plan-Net endpoint is being wired in -- unset by default, meaning no live payer source at all |

## SPA routing (static frontend hosting)

The frontend is a client-side-routed SPA (React Router). The hosting layer must rewrite all
non-asset paths to `index.html` (a request to `/providers/123` or `/signin` must serve the SPA shell,
not a 404) -- this is a hosting-layer configuration step (e.g. a catch-all rewrite rule), not
something the build output does itself. Verify this specifically: a hard refresh (not just
client-side navigation) on a deep route like `/providers/123` must not 404.

## Pre-deploy checklist

1. Run `docs/release-checklist.md`'s automated test suite -- all green.
2. Confirm every required env var above is set to a real, non-default, non-placeholder value.
3. Run a local production rehearsal first (see below) against a disposable Postgres before touching
   the real one.
4. Confirm the SPA rewrite rule is configured at the hosting layer (see above).
5. Confirm `CORS_ALLOWED_ORIGINS` exactly matches the real frontend's deployed origin (scheme +
   host, no trailing slash mismatch).

## Local production rehearsal (verified this phase)

Booted the packaged jar (default profile, not `prod`, to allow plain-HTTP local testing of the
underlying behavior) against a real, already-populated Postgres instance and confirmed:

- Flyway migrated the existing database in place (v9 -> v10) with zero data loss (492 pre-existing
  provider rows intact and queryable afterward).
- `GET /actuator/health` returned 200.
- Real search/detail/name-search/location-suggestion requests all returned correct data at healthy
  latency (see `docs/release-checklist.md` "Performance" for exact numbers).
- Separately (see `docs/threat-model.md` "Configuration leak"), booted with
  `SPRING_PROFILES_ACTIVE=prod` and each of: a missing JWT secret, a weak JWT secret, and an
  insecure cookie flag -- each refused to start with `ProductionSafetyValidator`'s specific error
  message, before any database connection was attempted. A fully valid `prod` config was confirmed
  to proceed past the guard (it then failed only because no real production database exists in this
  environment, which is expected).

**Not yet rehearsed this phase**: a full `prod`-profile boot against a real (not just
default-profile) HTTPS-fronted environment, since that requires an actual TLS-terminating reverse
proxy or load balancer, which is a real infrastructure choice outside this repository's scope to
simulate. The config-validation half of "prod" (the part `ProductionSafetyValidator` can check) is
verified; the network/TLS half depends on the actual hosting choice.

## Data bootstrap for a new environment

1. Start with an empty Postgres database; Flyway applies all migrations on first backend startup
   (`spring.flyway.enabled=true`, `ddl-auto=validate` -- the schema is never hand-created).
2. Reference data (specialties, demo payer/network/plan rows) seeds automatically via the migrations
   themselves (V1-V3, V7) -- no separate seeding step.
3. Real provider data: run a one-time NPPES import (`SPRING_PROFILES_ACTIVE=import`, see
   `docs/provider-ingestion.md`) or an operator-triggered CSV import. Neither runs automatically on
   normal startup.
4. Network evidence: only via the FHIR Plan-Net connector (if a real payer endpoint is configured)
   or explicit demo seeding (`DOCFIT_SYNTHETIC_INSURANCE_ENABLED=true`, non-production only, refused
   outright under `prod`).

## Gaps (honestly listed, not fixed this phase)

- No production-shaped `docker-compose.yml` bundling backend+frontend+postgres together --
  the existing compose file is dev-Postgres-only.
- No frontend Dockerfile (only needed if the chosen host requires a container rather than static
  hosting).
- No backup/restore rehearsal performed this phase.
- No CI/CD deployment automation reviewed/built beyond the existing `.github/workflows/ci.yml` test
  pipeline (which does not deploy anything).
- `AuthController.clientIp()` needs a trusted-proxy configuration decision once the real reverse
  proxy/load balancer topology is known (see `docs/api-security-matrix.md` "Known, deliberate,
  documented gap").

These are listed in the final phase report's blocker classification (MUST FIX BEFORE DEPLOYMENT /
SHOULD FIX DURING BETA / SAFE TO DEFER), not silently deferred.
