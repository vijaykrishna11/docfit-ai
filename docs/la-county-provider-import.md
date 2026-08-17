# LA County provider import

The API-vs-bulk decision and the calculated, bounded import plan for expanding provider data
beyond the original 6-ZIP Long Beach-area footprint, plus the actual results of running it this
phase. Companion docs: `docs/provider-source-research.md` (why NPPES generally),
`docs/la-county-geography-sources.md` (the geography this import runs against),
`docs/data-coverage.md` (resulting snapshot), `docs/provider-ingestion.md` (importer mechanics).

## Decision: NPPES API, not the bulk file, for this expansion

CLAUDE.md's own constraint rules out auto-downloading the national NPPES bulk file at runtime or
startup ("Do Not Auto-Download Nationwide NPPES"), and a multi-gigabyte national CSV remains far
more data than a 30-ZIP LA County expansion needs. The existing `NppesImportRunner` /
`NppesClient` API-based path (`docs/provider-source-research.md`) already does exactly the query
shape this expansion needs -- postal-code search, bounded by an explicit ZIP list -- so this phase
uses the API path, not the bulk file. The bulk-file path remains documented as the right choice for
a much larger future expansion (statewide California, where querying ZIP-by-ZIP would mean
thousands of individual requests); that decision is deferred, not rejected -- see
`docs/geospatial-scaling.md`.

## NPPES API limitations, confirmed empirically this phase

WebFetch could not retrieve the real content of the official NPI Registry API documentation pages
(JS-rendered), so these were confirmed by direct `curl` requests against the live public API
instead of trusted from documentation alone:

- **`limit` is silently capped at 200** regardless of what's requested. A request with
  `limit=1500` still returned `result_count: 200`. A single request can never return more than 200
  results, with no error or warning that more exist.
- **`skip` provides genuine pagination.** `skip=0` and `skip=200` for the identical query
  (`postal_code=90012&enumeration_type=NPI-1`) returned different first NPIs, confirming `skip`
  actually advances through the full result set rather than being ignored.
- **No documented numeric rate limit was found** (same finding as `docs/provider-source-research.md`).
  This import plan's own bounded request ceiling is the safety mechanism, not a rate limit the API
  publishes.

`NppesImportRunner` was updated this phase to page up to `MAX_PAGES_PER_QUERY = 3` (600 records)
per ZIP+entity-type combination instead of only ever fetching the first 200 -- previously, any
ZIP+type with more than 200 real NPPES matches was silently truncated with no indication of it in
logs or import records.

## The calculated import plan (recorded before running)

**Scope**: 30 ZIP codes, deliberately chosen from the newly-imported 295-row LA County geography
for genuine geographic breadth across the county rather than depth in one area -- one representative
ZIP per distinct major city/area, covering the Long Beach/Gateway Cities area (continuing the
original 6-ZIP footprint: `90802`, `90803`, `90806`, `90815`, `90712`, `90755`), South Bay
(`90501` Torrance, `90250` Hawthorne, `90247` Gardena, `90745` Carson), Westside
(`90401` Santa Monica, `90211` Beverly Hills, `90230` Culver City), San Gabriel Valley
(`91101` Pasadena, `91801` Alhambra, `91731` El Monte, `91790` West Covina, `91766` Pomona),
San Fernando Valley (`91401`, `91367` -- both resolve to "Los Angeles" as their Census place, since
these are LA City neighborhoods rather than separate incorporated cities, not a data error --
see `docs/la-county-geography-sources.md`), Verdugo/Tri-Cities (`91501` Burbank, `91046` Glendale),
Antelope Valley (`93534` Lancaster, `93551` Palmdale), central/south LA (`90002`), and additional
Gateway Cities (`90220` Compton, `90240` Downey, `90301` Inglewood, `90650` Norwalk, `90602`
Whittier).

**Bounds already built into the importer** (`NppesImportRunner`, this phase):
- `MAX_PAGES_PER_QUERY = 3` -- at most 600 records per ZIP+entity-type combination.
- `MAX_TOTAL_REQUESTS = 1200` -- an absolute ceiling across the whole run regardless of ZIP count.
- 2 entity types per ZIP (`NPI-1` individual, `NPI-2` organization).

**Calculated maximums for this run**: 30 ZIPs x 2 entity types x up to 3 pages = at most 180
requests, at most 200 records per request = a theoretical ceiling of 36,000 raw NPPES results
(before taxonomy/location mapping filters them down) -- far under the `MAX_TOTAL_REQUESTS = 1200`
safety ceiling, so the ceiling itself is not expected to bind this run. In practice, only a small
number of these 30 ZIPs (chiefly the already-dense `90802`) are expected to actually hit the
200-per-page threshold at all; most will return well under 200 raw results per ZIP+type and finish
in a single page.

**Import scope will be marked `PARTIAL`**, unconditionally -- this is a bounded 30-ZIP subset of
295 loaded LA County ZIPs (roughly 10% of loaded geography, and loaded geography itself is not a
claim of full LA County ZIP/ZCTA completeness), never a source-guaranteed-complete county query.

**Safety properties preserved from the existing importer** (verified in code, not just asserted):
unseen providers/locations/taxonomies from a prior import are never deactivated or deleted on a
partial import; a single bad source record is logged and skipped rather than aborting the run; the
importer only ever runs on the explicit `import` Spring profile, never automatically.

## Actual results

Run via:

```
POSTGRES_PORT=5433 POSTGRES_DB=docfitai POSTGRES_USER=docfitai POSTGRES_PASSWORD=changeme \
DOCFIT_NPPES_IMPORT_ZIP_CODES=90802,90803,90806,90815,90712,90755,90501,90250,90247,90745,90401,90211,90230,91101,91801,91731,91790,91766,91401,91367,91501,91046,93534,93551,90002,90220,90240,90301,90650,90602 \
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

| Metric | Value |
|---|---|
| Duration | ~3 min 19 sec (app startup + import; import proper ~3 min) |
| Total requests | 146 (of a possible 180 -- most ZIPs finished in fewer than the max 3 pages) |
| Raw records read from NPPES | 6,032 matched + mapped, 20,445 skipped (no recognized taxonomy code or no usable location -- expected; DocFit maps 27 of NUCC's several hundred taxonomy codes) |
| Providers created | 5,272 |
| Providers updated | 758 (re-touched across overlapping ZIP/entity-type queries, plus the original 582) |
| Locations created | 7,260 |
| Locations updated | 1,433 |
| Records failed | 2 (both `value too long for type character varying(2)` on `provider_location.state_code` -- two real NPPES source records report a non-2-character state value; each failed individually via its own transaction, no half-written provider or orphaned location resulted, confirmed by post-run row counts) |
| Data quality report | 0 errors, 0 warnings, 1 info -- clean |
| Import scope recorded | `PARTIAL` ("NPPES API, 30 ZIP(s), up to 3 page(s) (600 records) per ZIP/entity-type combination") |

**Database totals after this run**: 5,854 providers (3,956 individuals, 1,898 organizations),
8,095 locations -- up from the pre-run baseline of 582 providers / 835 locations.

**An honest note on location footprint vs. query scope**: locations now exist in 248 of the 295
loaded LA County ZIPs (`zip_geography` rows), even though only 30 ZIPs were directly queried. This
is not a discrepancy -- NPPES's `practiceLocations` field reports real additional offices a
provider has on file (`docs/provider-source-research.md`), so a single organization matched by a
query against one of the 30 scoped ZIPs can legitimately have real offices elsewhere in the county.
This does **not** mean provider data was comprehensively imported for all 248 of those ZIPs --
it means the 30-ZIP query surfaced providers whose *additional, real* locations happen to fall in
more places. Only the 30 explicitly queried ZIPs received a direct search; the wider footprint is
a byproduct of genuine multi-location provider records, not a broader import.

**Per-specialty counts, this run** (distinct providers per category, all 19 categories now
non-zero):

| Specialty | Providers |
|---|---|
| Psychiatry / Mental Health | 3,301 |
| Primary Care | 1,433 |
| Pediatrics | 255 |
| Obstetrics & Gynecology | 173 |
| Cardiology | 143 |
| General Surgery | 128 |
| Ophthalmology | 93 |
| Orthopedics | 86 |
| Physical Medicine & Rehabilitation | 85 |
| Pulmonology | 76 |
| Nephrology | 65 |
| Dermatology | 60 |
| Gastroenterology | 56 |
| Neurology | 47 |
| Endocrinology | 44 |
| Urology | 41 |
| Otolaryngology / ENT | 39 |
| Rheumatology | 35 |
| Allergy & Immunology | 17 |

**Multi-location distribution**: 4,769 providers with exactly 1 location, 729 with 2, 205 with 3,
declining smoothly down to a handful of large multi-site organizations (one with 31 locations).

See `docs/data-coverage.md` for the up-to-date snapshot and readiness labels.
