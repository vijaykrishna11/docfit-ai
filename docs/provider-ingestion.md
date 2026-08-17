# Provider data ingestion

Two importers share one idempotent upsert path. Neither runs automatically — both require
explicit operator action, and neither is wired into normal application startup. See
`docs/data-refresh-operations.md` for the operator runbook and `docs/data-coverage.md` for current
real numbers.

## Shared domain records and upsert path

`com.docfitai.backend.provider.ingestion` holds source-agnostic records any importer maps into:

- `ProviderIdentityRecord` — NPI, entity type (individual/organization), name fields.
- `ProviderLocationRecord` — one practice location (address, phone, fax, coordinates, precision).
- `ProviderTaxonomyRecord` — one taxonomy code + whether it's primary.
- `ProviderImportRecord` — one provider identity + its locations + its taxonomies, exactly what
  one source record (an NPPES result, or a CSV row group) produces.

`ProviderUpsertService.upsert(ProviderImportRecord)` is the single idempotent write path both
importers call:

- **Provider**: matched by NPI (unique). Existing providers are never duplicated.
- **Location**: matched by a normalized address identity (`LocationNormalizer.normalizedKey`,
  provider_id + normalized address line 1/2/city/state/ZIP5) — an exact database index enforces
  this (`uq_provider_location_normalized`). A location seen again updates phone/fax/coordinates in
  place rather than creating a duplicate row. The first location ever recorded for a provider is
  marked primary; later ones are not.
- **Taxonomy**: `(provider_id, taxonomy_code)` is the natural composite key; re-saving an
  already-known pair merges (update) rather than inserts.

This was verified against real production-shaped data, not just synthetic fixtures — see
"Real import results" below.

## Change detection

`ProviderUpsertService` also detects and records a bounded set of meaningful changes on an
*already-known* provider (never on a brand-new provider's initial import, since an initial state
is not a "change") into `provider_change_event`: `PROVIDER_NAME_CHANGED`,
`ORGANIZATION_NAME_CHANGED`, `LOCATION_ADDED`, `PHONE_CHANGED`, `TAXONOMY_ADDED`. Each event
carries a short old/new value (never a raw source payload) and the `data_import.id` it came from.

A provider's name can now actually change on re-import (it previously could not — the entity had
no setters, so a source name change was silently never reflected). Deliberately **not** detected:
`LOCATION_REMOVED_FROM_SOURCE`, `TAXONOMY_REMOVED`, `STATUS_CHANGED` — all three would require
reconciliation logic (comparing a "complete" import's full result set against existing rows) that
is intentionally not implemented, because a bounded/partial import can never safely distinguish
"genuinely removed at the source" from "just not in this batch" (CLAUDE.md "Partial Import
Safety"). `data_import` carries `scope_type`/`scope_description` columns as forward-looking
provenance for a future reconciliation feature, but nothing reads them yet.

No user-facing UI surfaces change events this phase — they're an operator/database-query signal
only (see `docs/data-refresh-operations.md` "Change events").

## NPPES importer

`NppesImportRunner`, gated behind the `import` Spring profile (unchanged trigger:
`./mvnw spring-boot:run -Dspring-boot.run.profiles=import`). For each of DocFit's demo ZIPs, it
now queries the NPI Registry API for **both** `NPI-1` (individual) and `NPI-2` (organization)
providers — previously only individuals were fetched.

`NppesProviderMapper.map(...)` (pure, unit-tested, no I/O) produces a `MappedProvider` combining:
- the `addresses` entry with `address_purpose = LOCATION`, and
- every entry in NPPES's own **`practiceLocations`** field — a real, documented NPI Registry API
  field for a provider's additional, non-primary practice locations. This is genuine multi-office
  data from the source, not a synthetic stand-in for the architecture (see
  `docs/provider-data-platform.md` for why that distinction matters).

Geocoding (ZIP → lat/lng via the existing `zip_geography` reference table) happens in the runner,
not the mapper, and every coordinate produced this way is labeled `ZIP_CENTROID` — never `EXACT`
or `ADDRESS_GEOCODE`, because that's not what it is.

A single malformed/unmappable source record is logged and skipped (`recordFailure()` on the
`DataImport` row); it does not abort the rest of the run.

`NppesClient` (`docs/provider-source-research.md`) has a bounded 15s request / 10s connect
timeout and retries a transient/5xx failure up to 3 times with a short pause between attempts —
never retries a 4xx, which is a real request problem, not a transient one.

## CSV importer

`ProviderCsvImportRunner`, off by default (`docfitai.import.csv.enabled=false` /
`DOCFIT_PROVIDER_CSV_IMPORT_ENABLED`). When enabled, it reads every `*.csv` file from an
**operator-configured local directory** (`docfitai.import.csv.source-directory` /
`DOCFIT_PROVIDER_CSV_SOURCE_DIR`) — never a request-supplied path; there is no
`/api/import?file=...`-style endpoint, and none should ever be added (CLAUDE.md 30, SSRF rule).

Each file is streamed line by line (`BufferedReader`, never loaded whole into memory) and each
row is upserted in its own small transaction via `ProviderUpsertService` — this is the "bounded
transaction size" this importer needs (CLAUDE.md 33); no separate batching/flush logic was added
on top of it, since nothing measured justified more complexity.

**CSV schema** (header required, columns can be in any order):
```
npi,entity_type,first_name,last_name,organization_name,address_line_1,address_line_2,
city,state_code,postal_code,phone,fax,latitude,longitude,taxonomy_codes
```
One row is one `(provider, location)` pair — a provider with multiple offices is multiple rows
sharing the same NPI, each producing another location through the same idempotent upsert path.
`taxonomy_codes` is semicolon-separated; the first code is treated as primary.

Parsing is deliberately simple comma-splitting, not full RFC 4180 quoted-field parsing — this
importer is for bounded, operator-prepared files, not arbitrary untrusted uploads, so a full CSV
grammar wasn't judged worth the added complexity (CLAUDE.md 29, "do not overabstract"). A field
containing a literal comma isn't supported; such a row fails clearly and is skipped rather than
silently misparsed.

## Import provenance (`data_import`)

Both importers write one `data_import` row per run: `source` (`NPPES` or `CSV`), `started_at`,
`completed_at`, `status` (`RUNNING` → `COMPLETED`/`PARTIAL`/`FAILED`), and counts
(`records_read`, `providers_created`, `providers_updated`, `locations_created`,
`locations_updated`, `records_failed`). `PARTIAL` means some records failed but at least one
provider was created/updated — a single bad row never turns a mostly-successful import into a
hard failure.

## Data quality report

`ProviderDataQualityService.runChecks()` runs after every import (both importers) and logs a
structured summary, each finding classified `ERROR`/`WARNING`/`INFO` (CLAUDE.md "Quality
Severity"): `ERROR` for a genuine defect (invalid NPI format, unusable identity, out-of-range
coordinates); `WARNING` for something an operator should look at but isn't necessarily wrong
(missing taxonomy/location, missing postal code); `INFO` for a low-priority, often-expected gap
(missing phone — matching CLAUDE.md's own example, "missing fax: do not even treat as issue"). It
is advisory only — it never rejects or deletes data, per CLAUDE.md 25 ("do not reject valid data
solely because some optional values are missing").

## Real import results (this phase, live, against the actual public NPI Registry API)

Run against DocFit's existing 6 demo ZIPs, now fetching both entity types:

```
First run:  recordsRead=502 providersCreated=221 providersUpdated=281
            locationsCreated=418 locationsUpdated=286 recordsFailed=0
            skippedNoTaxonomyOrLocationMatch=1813

Second run (immediately after, same data): recordsRead=502 providersCreated=0
            providersUpdated=502 locationsCreated=0 locationsUpdated=704 recordsFailed=0
```

The second run creating **zero** new providers and **zero** new locations while updating all 502
providers is the idempotency guarantee (CLAUDE.md 20) demonstrated against real data, not just
test fixtures.

The import surfaced genuine real-world multi-location organizations — e.g. one organization
provider ("SAMEDAY DOCTORS, P.C.") has **41 real practice locations** in NPPES's own data. Queried
through the search API, it appears exactly once, attached to its nearest office to the search
origin, with the other 40 available on its provider detail page — confirming the multi-location
architecture (CLAUDE.md 2, 10) against real, not synthetic, data. Data quality report for this
run: zero providers without a display name, taxonomy, or location; zero locations with a missing
postal code or invalid coordinates.

### Re-run after the specialty taxonomy expansion (data-expansion phase)

After adding 14 new NUCC taxonomy codes to `npi_taxonomy` (`docs/specialty-taxonomy-map.md`), the
NPPES importer was re-run against the same 6 demo ZIPs with **zero code changes** — it reads its
"known taxonomy codes" allowlist from the database at import time, so the expanded taxonomy set
was picked up automatically:

```
recordsRead=595 providersCreated=90 providersUpdated=505
locationsCreated=146 locationsUpdated=710 recordsFailed=0
```

90 genuinely new providers appeared — real providers whose taxonomy previously had no matching
DocFit AI category and were therefore skipped by the importer (`skippedNoTaxonomyOrLocationMatch`
in the earlier run). 13 of the 14 new specialty categories returned real search results afterward
(`docs/data-coverage.md` has the full per-specialty counts); this is real evidence the "no code
change needed" architecture claim holds, not just reasoning about it.
