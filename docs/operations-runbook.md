# Operations runbook

Practical day-2 operations reference: starting/stopping the app, what to check when something looks
wrong, and how to roll back. Companion to `docs/production-deployment-plan.md` (initial deploy) and
`docs/threat-model.md` (what's being defended against).

## Starting the backend

```
# Local dev (plain-HTTP defaults, insecure JWT placeholder -- never use these values anywhere else):
cd backend && ./mvnw spring-boot:run

# Against a real Postgres, default profile:
POSTGRES_PORT=5433 POSTGRES_DB=docfitai POSTGRES_USER=docfitai POSTGRES_PASSWORD=<real password> \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar

# Production profile (fails fast if any of these are missing/unsafe -- see ProductionSafetyValidator):
SPRING_PROFILES_ACTIVE=prod \
  JWT_SECRET=<real random 256+ bit secret> \
  CORS_ALLOWED_ORIGINS=https://<real frontend origin> \
  AUTH_COOKIE_SECURE=true \
  POSTGRES_PORT=5432 POSTGRES_DB=docfitai POSTGRES_USER=docfitai POSTGRES_PASSWORD=<real password> \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```

Health check: `GET /actuator/health` (the only exposed Actuator endpoint; `show-details=never`).

## Startup failure diagnosis

| Symptom | Likely cause | What to check |
|---|---|---|
| `IllegalStateException: Refusing to start under the 'prod' profile...` | `ProductionSafetyValidator` caught an unsafe config -- this is working as intended, not a bug | The exception message lists every specific problem (missing/weak JWT secret, insecure cookie flag, localhost CORS, synthetic-insurance or CSV-import flags left on). Fix the named env var(s) and restart. |
| `PlaceholderResolutionException: Could not resolve placeholder 'JWT_SECRET'` | `SPRING_PROFILES_ACTIVE=prod` but `JWT_SECRET` env var genuinely unset | Set `JWT_SECRET`. This fails even earlier than the validator above (Spring's own property resolution). |
| `Web server failed to start... Port 8080 was already in use` | Another instance (or a leftover process from a prior run) is already bound to the port | `netstat -ano \| grep 8080` (or platform equivalent), stop the stale process, or set `server.port`. |
| `FATAL: password authentication failed for user "docfitai"` | Wrong/missing `POSTGRES_PASSWORD`, or pointed at the wrong database | Confirm the target Postgres instance and credentials match; this is a real DB connection attempt, not an app bug. |
| Flyway migration failure on startup | A migration doesn't apply cleanly against the target database's current state | Check `flyway_schema_history` for the last successfully applied version; do not manually edit applied migration files -- add a new one. |

## Routine health checks

- `GET /actuator/health` returns `{"status":"UP"}` -- confirms the app is up and the DB connection
  pool is healthy (Spring Boot's default health indicator includes a DB check).
- A quick functional smoke check beyond the health endpoint: `GET /api/specialties` should return
  the fixed reference list (19 entries as of the data-expansion phase; see
  `docs/specialty-taxonomy-map.md`); an empty/error response despite a healthy `/actuator/health`
  would indicate a data problem, not a connectivity one.
- `GET /api/discovery/coverage` gives a quick real-count sanity check (provider/location/specialty
  counts, last import time) -- useful after a deploy or a restore to confirm the data looks right.

## Common operational tasks

### Rotate the JWT signing secret

Rotating `JWT_SECRET` immediately invalidates every currently-issued access token (they're stateless
and signature-verified) -- every signed-in user will need to silently re-authenticate via their
refresh cookie on their next request (this happens automatically via the frontend's 401-refresh-retry
flow; no user action needed unless their refresh cookie has also expired/been revoked). Refresh
tokens themselves are unaffected (they're opaque, hashed, DB-stored -- not signed with this secret).
Safe to do at any time; plan for a brief wave of refresh calls immediately after.

## Operator CLI reference (CLAUDE.md "Operator CLI Reference")

Every data-mutating operation in DocFit AI is triggered from the command line by an operator with
shell access to the deployment target -- there is deliberately **no public HTTP endpoint** that can
trigger an import, refresh, or geography load (CLAUDE.md "No Public Admin Endpoints"). All of these
are `CommandLineRunner`s gated by a Spring profile or a `docfitai.*.enabled` flag; the app exits
after each one-shot run (`import`/`refresh`/`quality-report` profiles) or continues running normally
afterward (`docfitai.import.geography.enabled` / `docfitai.import.csv.enabled`, which are plain
config flags, not profiles).

### Geography reference import

```
DOCFIT_GEOGRAPHY_IMPORT_ENABLED=true java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Loads the bundled, source-verified 295-row LA County ZIP/ZCTA reference set (or
`DOCFIT_GEOGRAPHY_SOURCE_PATH=<path>` for an operator-supplied file) into `zip_geography`.
Idempotent -- upserts by ZIP, safe to re-run. See `docs/la-county-geography-sources.md`.

### Bounded NPPES provider import

```
SPRING_PROFILES_ACTIVE=import DOCFIT_NPPES_IMPORT_ZIP_CODES=90802,90803,... \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Runs `NppesImportRunner` once, then exits. `DOCFIT_NPPES_IMPORT_ZIP_CODES` bounds the run to an
explicit, calculated ZIP subset -- omit it to process every row currently in `zip_geography` (only
sensible once that table itself is small/bounded). Idempotent -- safe to re-run; see
`docs/la-county-provider-import.md` and `docs/provider-ingestion.md`.

### Trigger a one-time CSV import

Set `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED=true` and `DOCFIT_PROVIDER_CSV_SOURCE_DIR=<path>` for that
one startup only, then unset `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED` before the next restart --
`ProductionSafetyValidator` refuses to start the `prod` profile at all if this flag is left on,
specifically to prevent it becoming an always-on background behavior.

### Operator dry-run (CSV or geography import)

```
DOCFIT_PROVIDER_CSV_IMPORT_ENABLED=true DOCFIT_PROVIDER_CSV_DRY_RUN=true DOCFIT_PROVIDER_CSV_SOURCE_DIR=<path> \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
# or:
DOCFIT_GEOGRAPHY_IMPORT_ENABLED=true DOCFIT_GEOGRAPHY_DRY_RUN=true \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Parses, validates, and counts every row exactly as a real import would -- **nothing is written**.
Reports records read/valid/invalid, recognized vs. unrecognized taxonomy codes (CSV only), and a
create-vs-update estimate. Use before a real import of an unfamiliar file to sanity-check it.

### Operator-triggered provider refresh (by NPI list)

```
SPRING_PROFILES_ACTIVE=refresh DOCFIT_REFRESH_NPIS=1234567890,1234567891 \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Re-fetches and re-upserts exactly the listed NPIs from NPPES, then exits. Never touches any
provider not in the list (CLAUDE.md "Partial Import Safety"). See "Provider refresh scheduler"
below for the optional automated version of this same operation.

### Provider refresh scheduler (optional, off by default)

```
DOCFIT_PROVIDER_REFRESH_ENABLED=true DOCFIT_REFRESH_NPIS=1234567890,1234567891 \
  DOCFIT_PROVIDER_REFRESH_CRON="0 0 3 * * *" \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Runs the same refresh as above on a cron schedule (default: daily at 3am) instead of a one-shot CLI
invocation. Off everywhere by default, including `prod` -- no background scheduling thread pool
exists at all unless `DOCFIT_PROVIDER_REFRESH_ENABLED=true` is explicitly set.
Overlap-protected by a Postgres advisory lock (`ProviderRefreshLock`): if a previous run is still in
flight when the next scheduled firing happens, that firing is skipped, not queued or run
concurrently. A failed run marks its `data_import` row `FAILED` and releases the lock cleanly --
it does not crash the app or block search.

### Address geocoding batch (optional precision upgrade)

```
DOCFIT_GEOCODE_ENABLED=true DOCFIT_GEOCODE_MAX_RECORDS=500 java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Geocodes up to `DOCFIT_GEOCODE_MAX_RECORDS` (hard ceiling 2,000 per run) `ZIP_CENTROID`
`provider_location` rows via the U.S. Census Geocoder, upgrading a real address-level match to
`ADDRESS_GEOCODE` precision. Never called from the search request path. Cached by normalized
address (`address_geocode_cache`) -- safe to re-run repeatedly; already-geocoded or already-tried
addresses are skipped without a new API call. See `docs/geocoding-strategy.md`.

### Data quality report (standalone, no import triggered)

```
SPRING_PROFILES_ACTIVE=quality-report java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Runs `ProviderDataQualityService.runChecks()` against the current data and logs the result, then
exits -- for checking data quality without also triggering an import. The same check also runs
automatically at the end of every import above.

### Coverage report

```
curl http://localhost:8080/api/discovery/coverage
```
The one operator-facing report that *is* a public HTTP endpoint (deliberately -- it's read-only,
reports only aggregate counts, and is what powers the frontend's own "Data sources & transparency"
panel). Reports real provider/location/specialty counts, reference-geography counts, and actual
provider-coverage counts as two clearly separate figures (CLAUDE.md "Reference Geography vs.
Provider Data" -- see `docs/data-coverage.md`).

### Rebuild the geo/search index after a large data load

Not required -- `idx_provider_location_lat_lng` and the other indexes are maintained automatically
by Postgres on every insert/update. No manual `REINDEX` step is part of normal operation.

## Rollback plan

DocFit AI ships as a single backend jar + a static frontend build; rollback is a redeploy of the
previous known-good artifact, not a special procedure:

1. **Application rollback**: redeploy the previous jar/frontend build. Stateless (JWT access
   tokens, in-memory rate limiter) -- no in-memory state is lost that matters across a redeploy.
2. **Database rollback**: Flyway migrations in this codebase are all additive (new tables/columns/
   indexes; V10 only adds an index). There is currently no destructive migration to roll back *from*.
   If a future migration is ever destructive, it must ship with a corresponding down-migration or an
   explicit backup-first runbook entry before it's added -- not assumed possible after the fact.
3. **Config rollback**: revert the specific env var(s) changed (e.g. `JWT_SECRET`,
   `CORS_ALLOWED_ORIGINS`) and restart. `ProductionSafetyValidator` will refuse to start if the
   rollback itself is unsafe, which is the intended safety net.

## Backup/restore rehearsal

Rehearsed this phase (data-expansion) using `pg_dump`/`pg_restore` against a disposable Postgres
container -- never against or into the real developer database:

```
docker exec <real-postgres-container> pg_dump -U docfitai -d docfitai -F c -f /tmp/backup.dump
docker cp <real-postgres-container>:/tmp/backup.dump ./backup.dump

docker run -d --name restore-rehearsal -e POSTGRES_DB=docfitai -e POSTGRES_USER=docfitai \
  -e POSTGRES_PASSWORD=<...> -p 5434:5432 postgres:17-alpine
docker cp ./backup.dump restore-rehearsal:/tmp/backup.dump
docker exec restore-rehearsal pg_restore -U docfitai -d docfitai --no-owner --no-privileges /tmp/backup.dump

# Compare row counts table-by-table between the two containers, then:
docker rm -f restore-rehearsal
```

Verified clean restore with matching row counts across every table added since that rehearsal --
including all of Care Discovery V3 (`provider_shortlist`, `shortlist_provider`,
`provider_data_report`), Care Navigator V4 (`user_provider_navigation`,
`provider_verification_item`, `user_reminder`, `user_saved_plan`), Data Expansion V5
(`provider_change_event`, `specialty.description`, `zip_geography.county`). No data loss, no
schema drift, no manual intervention needed beyond the dump/restore commands themselves.

**Repeated for LA County Expansion V5.1** (schema changed materially -- V17/V18/V19 migrations,
the new `address_geocode_cache` table, real LA County data now loaded): same procedure, same
disposable-container pattern, real dev database at the time (5,854 providers, 8,095 locations,
6,648 taxonomies, 295 `zip_geography` rows, 120 users, plus every other table). Every table's exact
row count (`COUNT(*)`, not the `pg_stat_user_tables` estimate) matched between the original and
restored database, including `address_geocode_cache` and `provider_change_event` at their real
current value of 0 rows each (neither feature had produced real data yet at rehearsal time -- an
empty table restoring correctly as empty is still a real, meaningful check). Database size at
rehearsal time: 52 MB. No data loss, no schema drift.

## Incident severity (starting point)

| Severity | Definition | Example | Response |
|---|---|---|---|
| SEV1 | Full outage or data-integrity/security incident | App down for all users; suspected credential/token compromise; wrong provider data being shown at scale | Immediate: roll back the triggering deploy/config change if identifiable; for a suspected secret compromise, rotate `JWT_SECRET` immediately (see above) |
| SEV2 | Significant degradation, workaround exists | Search endpoint slow/erroring for some queries; saved-provider/search feature broken but core search still works | Fix-forward or roll back within the current work session; not necessarily immediate |
| SEV3 | Minor, isolated | A single specialty/location edge case returns wrong results; a UI copy issue | Normal prioritization, no emergency response |

This is a starting framework, not a fully staffed on-call process -- DocFit AI currently has no
paging/on-call rotation defined. Add one before this matters (multi-person team, real user traffic
with an SLA).

## Logs

`logging.level.com.docfitai=INFO` by default (both dev and `prod`). No passwords, tokens, or
refresh-token values are ever logged (verified by grep this phase -- see `docs/privacy-data-flow.md`
"Logging"). Search/import events log counts and identifiers (provider id, NPI, specialty), not raw
request bodies.
