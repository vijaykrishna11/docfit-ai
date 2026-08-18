# Production deployment plan

What it actually takes to deploy DocFit AI, written from the real artifacts that exist in this
repository today -- not an aspirational target architecture. Companion to
`docs/operations-runbook.md` (day-2 operations) and `docs/threat-model.md`.

## Deployment shape

**Chosen topology for the first ($0) Render beta: SAME-ORIGIN, one service.** A single Render Web
Service runs one Java 21 process (via the root `Dockerfile`) that serves both the REST API
(`/api/**`) and the built React SPA (everything else) from one public origin. This is deliberately
**not** a separate static-site-plus-API-service split -- see "Cookie/CORS deployment topology --
RESOLVED via same-origin serving," below, for why that split is unsafe without a paid custom
domain, and why same-origin serving is the fix that avoids needing one.

- **One image, one process**: the root `Dockerfile` (multi-stage: Node 22 builds the frontend,
  Java 21 JDK builds the backend and embeds the frontend's `dist/` output into the jar's
  `static/` classpath resources, a final Java 21 JRE-only runtime stage runs it) — no Node, no
  Maven/JDK build tooling, and no secrets in the final image (verified this phase: `which node npm
  mvn` all report nothing inside the running container). Non-root runtime user, unchanged from the
  prior backend-only Dockerfile. Build from the **repository root**, not `backend/`.
- `backend/Dockerfile` (backend-only, no embedded frontend) is unchanged and still present --
  useful for local backend-only image builds or a future split-service topology once a custom
  domain makes that safe, but it is **not** what gets deployed for this beta.
- **Database**: external managed Postgres (or a self-run instance) -- not part of the application
  container. `docker-compose.yml` in this repo starts Postgres only, for local development.

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

## Cookie/CORS deployment topology -- RESOLVED via same-origin serving

**Resolved this phase.** The finding below (found during the prior release-prep phase) is why the
deployment shape at the top of this document is same-origin, single-service, rather than a
separate static-site-plus-API-service split. Kept in full for the record.

The refresh-token cookie is `HttpOnly; Secure; SameSite=Lax` (`AuthController`, deliberate CSRF
defense -- see `SecurityConfig`'s own doc comment; **unchanged this phase** -- `SameSite` was never
relaxed to `None`, and no CSRF assumption was weakened). **`SameSite=Lax` cookies are only sent on
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

Three options were identified; **option 2 was implemented this phase**:

1. ~~Custom domain with both services under one registrable domain~~ -- not chosen: requires
   buying/configuring a domain, and the beta is explicitly a $0 deployment.
2. **Serve frontend and backend from the same origin** (chosen) -- the root `Dockerfile` builds
   both and Spring Boot serves the built SPA (`SpaWebConfig`) alongside the API from one process,
   one origin. No cross-site request is ever made, so `SameSite=Lax` keeps working exactly as
   designed, with zero cost and zero domain purchase. See "Same-origin request flow," below.
3. ~~Relax `SameSite` to `None`~~ -- explicitly not done, per this phase's own directive: `SameSite`
   stays `Lax`, no CSRF assumption was weakened.

## Same-origin request flow (implemented this phase)

- **Frontend build**: `frontend/src/api/client.ts`'s `resolveApiBaseUrl()` defaults to
  `window.location.origin` in a production build (`import.meta.env.PROD`) and to
  `http://localhost:8080` in development -- never a hardcoded hostname. `VITE_API_BASE_URL` remains
  a supported explicit override for a future split topology.
- **Backend routing**: `SpaWebConfig` registers a resource handler on `/**` (classpath
  `static/`) with a custom `PathResourceResolver` that serves a real static file when one exists,
  falls back to `index.html` for genuine SPA routes (so React Router resolves deep links like
  `/providers/123` on a hard refresh, and its own client-side "not found" page for anything truly
  unknown), and returns a real 404 -- never the SPA shell -- for `/api/**`, `/actuator/**`, or a
  path that looks like a missing static asset (has a file extension). `SecurityConfig` gained one
  additional `permitAll()` rule (`SPA_SHELL_REQUEST_MATCHER`) so these same GET requests aren't
  blocked by `anyRequest().authenticated()` before ever reaching that resource handler -- scoped
  precisely to "GET, not `/api/**`, not `/actuator/**`," so no protected API endpoint's
  authorization changed.
- **CORS**: same-origin browser traffic doesn't strictly need it, but `ProductionSafetyValidator`
  still requires `CORS_ALLOWED_ORIGINS` to be set to a real, non-localhost value (unchanged --
  no reason to weaken this guard for a same-origin deployment; set it to the same
  `https://<service>.onrender.com` origin the service is deployed at).

## SPA routing

**No hosting-layer rewrite rule needed** -- this was the requirement for a *separate static-site*
topology (superseded, see above). With same-origin serving, `SpaWebConfig` (inside the same Spring
Boot process) handles the SPA-fallback itself; there is no separate static host to configure a
rewrite rule on. Verified directly this phase (not just reasoned about) against the real running
container: `GET /`, `/signin`, `/providers/123` all return the SPA shell (200); a genuinely unknown
route also returns the SPA shell (200, so React Router's own not-found page can render); a missing
hashed asset and an unmatched `/api/**` path both return real 404s, never the SPA shell.

## Pre-deploy checklist

1. Run `docs/release-checklist.md`'s automated test suite -- all green.
2. Confirm every required env var above is set to a real, non-default, non-placeholder value.
3. Run a local production rehearsal first (see below) against a disposable Postgres before touching
   the real one.
4. Confirm `CORS_ALLOWED_ORIGINS` is set to the deployed service's own origin, exactly (scheme +
   host, no trailing slash mismatch) -- same-origin deployment, so this is the one origin the
   service itself is deployed at; there's no separate frontend host to configure a rewrite rule on.

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
  response (see "Cookie/CORS deployment topology," above, for why this matters for hosting-topology
  selection -- at the time of this specific rehearsal, still unresolved; resolved in the phase
  documented immediately below).
- **Frontend production build**: `npm run build` with a non-localhost `VITE_API_BASE_URL` baked in
  cleanly; confirmed the production URL is present in the built bundle and zero occurrences of
  `localhost:8080` remain.
- Rehearsal containers/network fully torn down afterward -- nothing left running, no state
  persisted anywhere outside this rehearsal.

## Same-origin Docker + production rehearsal (Render same-origin deployment phase)

Built and ran the new **root** `Dockerfile` (not `backend/Dockerfile`) -- built from the repository
root, embedding the frontend into the backend jar -- against a fresh disposable Postgres:

- **Build**: succeeded. Final image contains no `node`, `npm`, or `mvn` (`which` returns nothing
  for all three inside the running container -- confirmed directly, not assumed); content size
  237MB (JRE + fat jar only).
- **Empty-DB migration path**: same clean result as the prior rehearsal (V1 through V19 applied,
  app started in ~15s).
- **`prod`-profile config refusals**: re-confirmed working (missing/weak JWT secret, placeholder
  DB password) -- unaffected by the same-origin change, as expected.
- **A fully valid `prod` config, full checklist, all verified against the real running container**:
  `GET /actuator/health` -> 200; `GET /` -> real React HTML (`<!doctype html>...<title>DocFit
  AI</title>...`); `GET /signin` and `GET /providers/123` (direct navigation, not client-side
  routing) -> 200, the same real SPA HTML; `GET /api/specialties` -> real JSON; `GET
  /api/discovery/this-does-not-exist` -> a real `404` JSON error (`"No static resource
  api/discovery/this-does-not-exist"`), never the SPA shell; a real static asset (`/assets/*.js`)
  -> 200 with the correct content-type; a deliberately-missing hashed asset -> a real 404 (`404`,
  `application/json`), never silently swapped for HTML; `POST /api/auth/register` -> `201` with a
  real access token; `POST /api/auth/login` -> `200` with `Set-Cookie:` carrying exactly `Secure;
  HttpOnly; SameSite=Lax` -- **unchanged from the prior (would-be cross-site) topology**, now
  actually usable because the request that sends it back on refresh is same-origin.
- **Frontend bundle inspected directly**: exactly one occurrence of the string `localhost:8080` in
  the built, minified bundle -- traced to the unreachable dead-code fallback branch inside
  `resolveApiBaseUrl`'s minified body (`e||(t?n:"http://localhost:8080")`); the actual computed
  constant used at runtime evaluates to `window.location.origin`, confirmed both by direct
  expression evaluation and by the real `/api/specialties` call above succeeding against the same
  origin serving the page. Documented honestly rather than claimed as a clean zero -- it's a benign
  minifier artifact, not a functional reference.
- **E2E (Playwright, real browser) against the real running container**: the location-suggestions/
  coverage-panel/specialty-selector/unsupported-area test group (6 scenarios) passed cleanly against
  a small bounded real NPPES import (568 providers, 2 ZIPs) loaded into the rehearsal database.
  A separate group of search-result-card tests failed against this same rehearsal -- traced
  directly (via a DB query and a direct API call) to the 2-ZIP sample genuinely containing zero
  Cardiology-taxonomy providers near the tested ZIP, not a routing/topology defect; a Primary-Care
  search against the same data returned real results correctly through the same-origin path.
- Rehearsal containers/network fully torn down afterward.

**Still not rehearsed**: a full `prod`-profile boot behind a real TLS-terminating reverse proxy or
load balancer (an actual infrastructure/hosting choice outside this repository's scope to simulate
locally).

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

- ~~Cookie/CORS topology decision not made~~ -- **resolved this phase**: same-origin serving (see
  "Deployment shape" and "Same-origin request flow," above).
- No production-shaped `docker-compose.yml` bundling backend+frontend+postgres together --
  the existing compose file is dev-Postgres-only. Less pressing now that frontend+backend are one
  container, but a local compose file exercising the real root `Dockerfile` against disposable
  Postgres would still be a reasonable future convenience.
- A separate frontend Dockerfile is no longer relevant to the chosen topology (the frontend is
  embedded into the backend image, not deployed as its own static host) -- would only matter again
  if a future split-service topology (custom domain) is chosen instead.
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
