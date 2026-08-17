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

### Trigger a one-time NPPES import

```
SPRING_PROFILES_ACTIVE=import java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
```
Runs `NppesProviderImportRunner` once at startup, then the app continues running normally (does not
re-run on subsequent restarts unless the `import` profile is explicitly reactivated). Idempotent --
safe to re-run; see `docs/provider-ingestion.md`.

### Trigger a one-time CSV import

Set `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED=true` and `DOCFIT_PROVIDER_CSV_SOURCE_DIR=<path>` for that
one startup only, then unset `DOCFIT_PROVIDER_CSV_IMPORT_ENABLED` before the next restart --
`ProductionSafetyValidator` refuses to start the `prod` profile at all if this flag is left on,
specifically to prevent it becoming an always-on background behavior.

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

Verified clean restore with matching row counts across every table added since the last rehearsal
-- including all of Care Discovery V3 (`provider_shortlist`, `shortlist_provider`,
`provider_data_report`), Care Navigator V4 (`user_provider_navigation`,
`provider_verification_item`, `user_reminder`, `user_saved_plan`), and this phase's own additions
(`provider_change_event`, `specialty.description`, `zip_geography.county`). No data loss, no
schema drift, no manual intervention needed beyond the dump/restore commands themselves.

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
