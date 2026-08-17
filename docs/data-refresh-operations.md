# Data refresh operations

Operator runbook for provider data imports/refreshes. Companion docs: `docs/provider-ingestion.md`
(architecture), `docs/data-coverage.md` (current real numbers), `docs/provider-source-research.md`
(source decisions).

## Import/refresh modes (LA County Expansion V5.1 adds four more)

| Mode | Trigger | Default |
|---|---|---|
| Geography reference import | `docfitai.import.geography.enabled=true` | `enabled=false` |
| NPPES bulk import (live API, ZIP-scoped) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=import` (optionally bounded via `DOCFIT_NPPES_IMPORT_ZIP_CODES`) | Never runs automatically -- requires the explicit `import` Spring profile |
| CSV (bounded, operator-prepared files) | `docfitai.import.csv.enabled=true` + `docfitai.import.csv.source-directory=<path>` | `enabled=false` |
| NPPES refresh (bounded NPI list) | `SPRING_PROFILES_ACTIVE=refresh` + `DOCFIT_REFRESH_NPIS=<comma-list>` | Never runs automatically -- requires the explicit `refresh` Spring profile |
| Refresh scheduler (recurring, same NPI list) | `DOCFIT_PROVIDER_REFRESH_ENABLED=true` | `enabled=false` everywhere, including `prod` |
| Address geocoding batch | `DOCFIT_GEOCODE_ENABLED=true` | `enabled=false` |
| Dry-run (CSV or geography) | `DOCFIT_PROVIDER_CSV_DRY_RUN=true` / `DOCFIT_GEOGRAPHY_DRY_RUN=true` | `false` |

None of these ever runs as part of normal application startup unless explicitly enabled
(CLAUDE.md "Do Not Auto-Download Nationwide NPPES") -- a production deployment's normal boot never
triggers a provider import, geography import, refresh, or geocode. Full command examples for every
mode: `docs/operations-runbook.md` ("Operator CLI reference"). There is deliberately no public HTTP
endpoint for any of these -- all are CLI/profile/flag-gated.

## Starting an import

**NPPES** (updates every ZIP currently in `zip_geography`, both individual and organization
providers):
```
cd backend
POSTGRES_PORT=5433 POSTGRES_DB=docfitai POSTGRES_USER=docfitai POSTGRES_PASSWORD=<...> \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```
This is a one-shot `CommandLineRunner` -- it starts the full Spring context (including Tomcat on
the configured port), runs the import, prints a summary line, and exits the JVM
(`SpringApplication.exit`). **Stop any already-running instance on the same port first** -- it will
otherwise fail to bind.

**CSV**:
```
DOCFIT_PROVIDER_CSV_IMPORT_ENABLED=true DOCFIT_PROVIDER_CSV_SOURCE_DIR=/path/to/csvs \
  ./mvnw spring-boot:run
```
Reads every `*.csv` file in the configured directory (see `docs/provider-ingestion.md` for the
required header). The directory is operator-configured server-side config only -- there is no
request parameter or endpoint that accepts a file path (CLAUDE.md "CSV Security").

## Monitoring

Both importers log structured progress lines (`NppesImportRunner`/`ProviderCsvImportRunner`,
INFO level) -- per-ZIP/per-file counts as they go, then one final summary line with
`status`/`recordsRead`/`providersCreated`/`providersUpdated`/`locationsCreated`/`locationsUpdated`/
`recordsFailed`. The same numbers are durably recorded in the `data_import` table
(`docs/provider-ingestion.md` "Import provenance"), queryable after the fact:

```sql
SELECT source, status, records_read, providers_created, providers_updated,
       locations_created, locations_updated, records_failed, started_at, completed_at
FROM data_import ORDER BY started_at DESC LIMIT 10;
```

## Interpreting partial failure

`status = PARTIAL` means some rows failed but at least one provider was created or updated -- a
single bad source row never aborts the whole run (CLAUDE.md "Failed Row Handling"). This is a
normal, expected outcome for a live-data import, not necessarily something to fix. `records_failed
> 0` with detail only in the application log (not a separate failed-rows table this phase) -- check
the log around the import's timestamp for the specific `NPI`/file:line and error category.

`status = FAILED` means the run itself errored out before completing (e.g. the source was
unreachable for every request) -- check the log for the underlying exception.

## Retrying

**Always safe to just re-run.** Both importers are fully idempotent (CLAUDE.md "Idempotency,"
verified directly this phase and in `docs/provider-ingestion.md`'s "Real import results"):
re-running against the same source data creates zero new providers/locations and updates existing
ones in place. There is no manual cleanup step and no need to touch Flyway migration state --
retrying an import is not a schema operation.

## Verifying counts after an import

```sql
SELECT count(*) FROM provider;
SELECT count(*) FROM provider_location;
```
Or via `docs/data-coverage.md`'s queries for a per-specialty breakdown. The application also runs
`ProviderDataQualityService.runChecks()` automatically at the end of every import and logs a
summary (`docs/provider-ingestion.md` "Data quality report") -- review any `ERROR`-severity
findings there before considering an import "clean."

## Change events

If any already-known provider's name, an existing location's phone, a new location, or a new
taxonomy was detected during the import, a row was written to `provider_change_event`
(`docs/provider-ingestion.md`, `ChangeType`). Query recent changes:

```sql
SELECT provider_id, change_type, old_value, new_value, created_at
FROM provider_change_event
WHERE source_import_id = <the data_import.id from the run>
ORDER BY created_at DESC;
```

**As of LA County Expansion V5.1**, `ProviderChangeSummaryService` automatically logs a short,
human-readable summary at the end of every NPPES/CSV import -- "2 locations added, 1 phone number
changed, 1 provider name changed" (or "No tracked changes" if nothing did) -- so an operator
doesn't have to run the SQL above just to get the gist. The raw query above is still the way to see
individual changes.

No user-facing UI surfaces this yet (CLAUDE.md "Provider Change UI": deliberately deferred --
this phase's real LA County import happened to produce zero change events of its own, mostly
re-touches of identical data within one run, so there's no real change data yet to build and demo
a saved-provider-facing view against). Still an operator/database-query-plus-log-line signal today,
not a product feature.

## Rollback

There is no automated "undo an import." Because every write is either a genuine new row or an
in-place update to a field that changed, reversing one specific import's effects after later
imports may have also touched the same rows is not a well-defined operation -- document this
honestly rather than promise a rollback button that doesn't exist (CLAUDE.md "Rollback": "No fake
easy rollback claim"). If a bad import needs to be undone:

1. **Before running any bulk import against a production-like environment, take a database
   backup.** This is the real rollback mechanism -- restore from that backup.
2. Application-level rollback (redeploying older code) never reverts data changes an import already
   committed -- code and data are independent here.
3. For a small, well-understood bad batch (e.g. one CSV file processed with a known mapping bug),
   a manual, reviewed `DELETE`/`UPDATE` against the specific rows is more practical than trying to
   automate general-purpose import reversal.

## Backup considerations after a bulk import

See `docs/operations-runbook.md` for the general backup/restore rehearsal. After any bulk provider
import, the new/changed rows (provider, provider_location, provider_taxonomy, data_import,
provider_change_event) should be included in the next backup cycle -- no special handling beyond
that; these are ordinary tables, not something requiring a different backup strategy.

## No downtime required

Search continues to work normally while an import runs (CLAUDE.md "Rolling Import" / "API
Performance Regression"): the importer commits in small, bounded transactions (one provider per
NPPES record, one row per CSV line) rather than one giant transaction, so it never holds a
long-lived lock across the whole run. A concurrent search may see a mix of pre- and post-import
data mid-run -- expected and acceptable, since each individual committed row is always internally
consistent (a provider's own locations/taxonomies are written before that transaction commits).
