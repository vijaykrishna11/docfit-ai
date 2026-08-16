# Provider data ingestion

Two importers share one idempotent upsert path. Neither runs automatically — both require
explicit operator action, and neither is wired into normal application startup.

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
structured summary: providers without a display name, without any taxonomy, without any location;
locations missing a postal code; locations with an out-of-range latitude/longitude. It's advisory
only — it never rejects or deletes data, per CLAUDE.md 25 ("do not reject valid data solely
because some optional values are missing").

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
