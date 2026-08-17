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
without safe values for these (verified this phase by actually booting the real container image
against each bad value below and confirming the exact refusal message, not just reading the code):

| Variable | Requirement | Why |
|---|---|---|
| `JWT_SECRET` | Random, 256+ bits (32+ characters), not a known placeholder | Signs access tokens; a weak/guessable secret allows token forgery |
| `CORS_ALLOWED_ORIGINS` | The real frontend origin(s), never localhost/127.0.0.1 | Browsers enforce CORS based on this; wrong value either blocks the real frontend or (if wildcarded) opens the API to any origin |
| `AUTH_COOKIE_SECURE` | `true` (this is already the `prod` profile's default) | Refresh-token cookie must never be sent over plain HTTP |
| `SPRING_DATASOURCE_URL` + `POSTGRES_PASSWORD` (or equivalent `spring.datasource.*`) | Real production database URL and password -- **new this phase**: `POSTGRES_PASSWORD` is now also guarded against known placeholders (`changeme`/`password`/`postgres`/empty), same as the JWT secret. Previously unguarded -- a real gap found during this phase's release-prep config audit. | A guessable DB password was previously able to silently reach production |
| `DOCFIT_SYNTHETIC_INSURANCE_ENABLED` | Must be unset or `false` (already the default) | Synthetic network evidence must never seed in production |
| `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED` | Must be unset or `false` (already the default) | Bulk import must be operator-triggered, not always-on |
| `DOCFIT_GEOGRAPHY_IMPORT_ENABLED` | Must be unset or `false` (already the default) | Same reasoning, added when the geography importer shipped |
| `DOCFIT_GEOCODE_ENABLED` | Must be unset or `false` (already the default) | Same reasoning, added when the geocoding pipeline shipped |

**New this phase**: `PORT` -- `server.port` is now explicitly bound to `${PORT:8080}` (previously
unbound, meaning a hosting platform's injected `PORT` value was silently ignored and the app always
listened on 8080 regardless). Most PaaS platforms (Render, Heroku) set `PORT` automatically; no
action needed unless the platform's convention differs.

Optional but recommended:

| Variable | Purpose |
|---|---|
| `ACCESS_TOKEN_TTL_MINUTES` / `REFRESH_TOKEN_TTL_DAYS` | Session lifetime tuning (defaults: 15 min / 30 days) |
| `AUTH_RATE_LIMIT_MAX_ATTEMPTS` / `AUTH_RATE_LIMIT_WINDOW_MINUTES` | Login/register throttling tuning (defaults: 10 attempts / 5 min) |
| `FHIR_PLAN_NET_BASE_URL` | Only if a specific, vetted payer FHIR Plan-Net endpoint is being wired in -- unset by default, meaning no live payer source at all |

## Critical: cookie/CORS deployment topology (found during this phase's release prep)

The refresh-token cookie is `HttpOnly; Secure; SameSite=Lax` (`AuthController`, deliberate CSRF
defense -- see `SecurityConfig`'s own doc comment). **`SameSite=Lax` cookies are only sent on
same-site requests.** "Same-site" is defined by the browser's registrable-domain (eTLD+1)
computation, using the Public Suffix List -- not by "looks like the same company."

**This has a real, confirmed consequence for the most likely first hosting choice.** `onrender.com`
(Render's default subdomain) **is itself on the Public Suffix List** -- confirmed via the list
directly and via a real, documented case on Render's own community forum of a team hitting exactly
this problem deploying a frontend and API on two separate default `*.onrender.com` subdomains
(`https://render.discourse.group/t/setting-cookies-onrender-com/7886`). This means **each
`*.onrender.com` service is its own "site,"** and a refresh cookie set by
`docfit-api.onrender.com` will be silently withheld by the browser on a cross-site `fetch()` POST
from `docfit-web.onrender.com` to `/api/auth/refresh` -- login would appear to work (the initial
`Set-Cookie` happens same-response), but the session would silently fail to persist past the access
token's short TTL (15 min default), and refresh-cookie-dependent flows would break in a way that is
easy to miss in a quick manual smoke test but breaks real usage.

**This must be resolved before choosing a final topology** -- options, not decided here:

1. **Custom domain with both services under one registrable domain** (e.g. `app.docfit.example`
   for the frontend, `api.docfit.example` for the backend) -- subdomains of the same registrable
   domain ARE same-site for `SameSite=Lax` purposes. This is the standard fix and requires owning a
   real domain plus configuring it at the hosting platform.
2. **Serve frontend and backend from the same origin** (e.g. the backend serves the built static
   frontend, or a reverse proxy puts both behind one hostname) -- avoids the cross-site problem
   entirely, at the cost of losing the simplicity of a pure static-host + separate API-host split.
3. **Relax `SameSite` to `None`** (requires `Secure=true`, already the prod default) -- makes the
   cookie work cross-site, but reopens exactly the CSRF vector `SecurityConfig`'s own doc comment
   says `Lax` was chosen to close. Would need a real CSRF-protection decision alongside it. **Not
   done this phase** -- a security-relevant code change, out of this release-prep phase's scope
   ("do not make product feature changes").

No topology was selected or deployed this phase. See `docs/web-deployment-checklist.md` and the
final release report for the recommendation.

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

## Local production rehearsal (data-expansion phase)

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

## Docker + production rehearsal (web-beta release-prep phase)

Actually built and ran the real container image (`docker build -f backend/Dockerfile`) against a
disposable, genuinely empty Postgres container on a private Docker network -- not just read the
Dockerfile and assumed it works:

- **Build**: succeeded (multi-stage, Java 21 JDK builder -> JRE runtime, non-root `docfitai` user,
  no secrets baked in, wildcard `COPY --from=builder ... backend-*.jar` so the version bump to
  `0.1.0-beta.1` needed no Dockerfile change).
- **Empty-DB migration path**: booted against a genuinely fresh Postgres container -- Flyway applied
  all 19 migrations cleanly (V1 through V19, `create address geocode cache`) and the app started
  (`Started BackendApplication in 13.924 seconds`).
- **`prod`-profile config refusals, verified in the real container** (not just unit tests): missing
  `JWT_SECRET`, a weak `JWT_SECRET`, and (new this phase) a placeholder `POSTGRES_PASSWORD`
  (`changeme`) each produced the exact expected `IllegalStateException` refusal message before any
  meaningful startup work happened.
- **A fully valid `prod` config started successfully** against the disposable database and served
  real traffic: `GET /actuator/health` -> `200 {"status":"UP"}`; `GET /api/specialties` -> the real
  19 seeded specialties; `GET /api/discovery/coverage` -> `providerCount: 0, locationCount: 0`
  against the genuinely empty DB (direct proof no import auto-triggers on startup --
  `geographyZipCount: 6` from the V3 migration seed, correctly present); `POST /api/auth/register`
  -> `201` with a real issued access token; `POST /api/auth/login` -> `200` with a `Set-Cookie`
  header confirmed to carry exactly `Secure; HttpOnly; SameSite=Lax` in the real `prod`-profile
  response (see "Critical: cookie/CORS deployment topology," above, for why this matters for
  hosting-topology selection).
- **Frontend production build**: `npm run build` with a non-localhost `VITE_API_BASE_URL` baked in
  cleanly; confirmed the production URL is present in the built bundle and zero occurrences of
  `localhost:8080` remain.
- Rehearsal containers/network fully torn down afterward -- nothing left running, no state
  persisted anywhere outside this rehearsal.

**Still not rehearsed**: a full `prod`-profile boot behind a real TLS-terminating reverse proxy or
load balancer (an actual infrastructure/hosting choice outside this repository's scope to simulate
locally), and the cross-site cookie topology question itself (needs a real chosen domain
architecture to test against, not just reasoned about).

## Data bootstrap for a new environment

1. Start with an empty Postgres database; Flyway applies all migrations on first backend startup
   (`spring.flyway.enabled=true`, `ddl-auto=validate` -- the schema is never hand-created). Verified
   this phase against a genuinely fresh disposable Postgres container -- see "Docker + production
   rehearsal," above.
2. Reference data (specialties, demo payer/network/plan rows, and a small 6-ZIP `zip_geography`
   seed) seeds automatically via the migrations themselves (V1-V3, V7) -- no separate seeding step.
   This alone is **not** LA County coverage -- see step 3.
3. **Geography reference data** (as of LA County Expansion V5.1): run the operator-controlled
   geography importer (`DOCFIT_GEOGRAPHY_IMPORT_ENABLED=true`) to load the real, source-verified
   295-ZIP LA County reference set bundled in the jar. Does not run automatically.
4. Real provider data: run a one-time, bounded NPPES import (`SPRING_PROFILES_ACTIVE=import`,
   optionally scoped via `DOCFIT_NPPES_IMPORT_ZIP_CODES`, see `docs/provider-ingestion.md` and
   `docs/la-county-provider-import.md`) or an operator-triggered CSV import. Neither runs
   automatically on normal startup. **Production starts with zero providers until this step is
   deliberately run** -- confirmed directly this phase (`providerCount: 0` on a fresh boot).
5. Network evidence: only via the FHIR Plan-Net connector (if a real payer endpoint is configured)
   or explicit demo seeding (`DOCFIT_SYNTHETIC_INSURANCE_ENABLED=true`, non-production only, refused
   outright under `prod`).
6. Full first-deployment procedure with exact commands: `docs/web-deployment-checklist.md`.

## Gaps (honestly listed, not fixed this phase)

- **Cookie/CORS topology decision not made** -- see "Critical" section above. This is the single
  highest-priority open item before choosing a hosting provider/domain setup.
- No production-shaped `docker-compose.yml` bundling backend+frontend+postgres together --
  the existing compose file is dev-Postgres-only.
- No frontend Dockerfile (only needed if the chosen host requires a container rather than static
  hosting; Render Static Site and most static hosts don't need one).
- No CI/CD deployment automation reviewed/built beyond the existing `.github/workflows/ci.yml` test
  pipeline (which does not deploy anything) -- deliberately out of scope for this release-prep
  phase.
- No E2E suite in CI (requires a live Postgres+backend+frontend triad CI doesn't set up yet) --
  E2E was run locally against the release branch instead (2x, all green; see the release report).
- `AuthController.clientIp()` needs a trusted-proxy configuration decision once the real reverse
  proxy/load balancer topology is known (see `docs/api-security-matrix.md` "Known, deliberate,
  documented gap").
- Address-level geocoding pipeline exists but has not been run against real production-scale data
  yet (see `docs/geocoding-strategy.md`) -- not a blocker, an operator-triggered follow-up.

These are listed in the final phase report's blocker classification (MUST FIX BEFORE DEPLOYMENT /
SHOULD FIX DURING BETA / SAFE TO DEFER), not silently deferred.
