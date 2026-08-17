# Data coverage

What DocFit AI's database actually contains right now, as measured directly against the local
development database on the date this document was last updated -- not an aspiration, not a
marketing claim. Companion docs: `docs/provider-ingestion.md` (how this data got here),
`docs/specialty-taxonomy-map.md` (specialty detail), `docs/geospatial-scaling.md` (performance at
this and larger simulated scale).

CLAUDE.md's own principle for this document: *"DocFit should not look larger than it really is. If
we have 500 providers, say 500. If we cover Long Beach, say Long Beach."*

## Snapshot (LA County Expansion V5.1, after the real bounded 30-ZIP NPPES import)

| Metric | Count |
|---|---|
| Providers | 5,854 (1,898 organizations, 3,956 individuals) |
| Practice locations | 8,095 |
| Specialty categories | 19 (all non-zero) |
| NUCC taxonomy codes mapped | 27 |
| Geography reference rows (`zip_geography`) | 295 (real, source-verified LA County ZCTAs -- see `docs/la-county-geography-sources.md`) |
| `zip_geography` rows with at least one provider location | 248 of 295 |
| Counties represented (geography reference) | 1 (Los Angeles) |
| ZIPs directly queried against NPPES this phase | 30 (see `docs/la-county-provider-import.md`) |

## Geographic coverage: real LA County reference geography, bounded provider data

**Reference geography loaded**: 295 real ZIP Code Tabulation Areas (ZCTAs) whose primary county is
Los Angeles, sourced directly from U.S. Census Bureau files (`docs/la-county-geography-sources.md`).
This is a large, honest step up from the original 6-ZIP demo footprint, but it is reference
geography, not provider data -- see the next paragraph.

**Provider data actually loaded**: NPPES was directly queried for only 30 of those 295 ZIPs, chosen
for geographic breadth across the county (South Bay, Westside, San Gabriel Valley, San Fernando
Valley, Antelope Valley, Gateway Cities -- full list in `docs/la-county-provider-import.md`). The
resulting provider records' own reported `practiceLocations` (real additional offices) happen to
land in 248 of the 295 loaded ZIPs, but **only the 30 queried ZIPs received a direct, deliberate
search** -- the wider location footprint is a byproduct of genuine multi-location provider data,
not a claim that all 295 ZIPs were comprehensively searched. Do not read "248 ZIPs have at least
one location" as "LA County provider coverage" -- most of those 248 have only the handful of
locations that happened to be reported by a provider matched from one of the 30 queried ZIPs, not
a real search of that ZIP's own provider population.

**Not loaded**: the remaining 265 of 295 loaded-geography ZIPs have never been directly queried;
the rest of Southern California and the rest of California remain entirely out of scope (deliberately
deferred -- see "Readiness labels," below).

## Specialty coverage: real counts, this run

Distinct providers per specialty category, counted directly from the database (not location rows,
not taxonomy rows) -- see `docs/la-county-provider-import.md` for the full current table. All 19
categories now have real, non-zero data (Allergy & Immunology is the smallest at 17; Psychiatry /
Mental Health the largest at 3,301) -- a meaningful improvement over the prior 6-ZIP snapshot, where
3 categories had exactly 1 provider.

## Readiness labels (CLAUDE.md "Honest Readiness Labels")

| | Architecture | Data actually loaded |
|---|---|---|
| **LA County** (all of it) | **YES** -- `NppesImportRunner` already fetches by ZIP + entity type + reads its taxonomy allowlist dynamically from `npi_taxonomy`; querying more of the 295 already-loaded LA County ZIPs needs zero code changes, only a longer `DOCFIT_NPPES_IMPORT_ZIP_CODES` list. | **PARTIAL** -- reference geography for all 295 loaded ZCTAs exists, but only 30 of them were directly queried for providers. This is real, meaningful progress over the prior 6-ZIP/NO state, not full county coverage. |
| **California** (statewide) | **PARTIAL** -- the same import mechanism works for any CA ZIP, but `zip_geography` has no bulk California ZCTA import wired up yet; county reference data exists for exactly 1 county (Los Angeles). | **NO**. |

Do not read "architecture: YES" as "ready to flip a switch with no further work" -- it means the
*code path* that would consume more ZIPs already exists and was proven this phase (a real 30-ZIP,
146-request, 5,272-provider-created run), not that scaling to hundreds of ZIPs is risk-free without
also revisiting pacing toward NPPES and reviewing performance at that data volume (see
`docs/geospatial-scaling.md`).

## If a larger import is ever needed

1. **Geography is already loaded for LA County.** All 295 real LA County ZCTAs are in
   `zip_geography` (`docs/la-county-geography-sources.md`) -- no further geography work needed for
   an LA-County-scale expansion. Statewide California would still need a new bulk geography import.
2. **Run the NPPES importer** (`./mvnw spring-boot:run -Dspring-boot.run.profiles=import`) with a
   longer `DOCFIT_NPPES_IMPORT_ZIP_CODES` list drawn from the already-loaded 295 ZIPs. No code
   change needed -- verified this phase with a real 30-ZIP run.
3. **Run the data quality report** (`ProviderDataQualityService`, runs automatically after every
   import) and review any `ERROR`-severity findings before considering the import "done." This
   phase's run: 0 errors, 0 warnings.
4. **Re-measure search performance** at the new real row count -- do not assume a synthetic
   benchmark's numbers transfer exactly; re-run `EXPLAIN ANALYZE` against the real data.

## What this document is not

Not a claim about clinical quality, provider availability, or insurance participation -- purely a
factual count of what rows exist in the database. See `docs/insurance-network-architecture.md` for
why provider-data freshness and network-evidence freshness are tracked completely separately.
