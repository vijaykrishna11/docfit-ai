# Data coverage

What DocFit AI's database actually contains right now, as measured directly against the local
development database on the date this document was last updated -- not an aspiration, not a
marketing claim. Companion docs: `docs/provider-ingestion.md` (how this data got here),
`docs/specialty-taxonomy-map.md` (specialty detail), `docs/geospatial-scaling.md` (performance at
this and larger simulated scale).

CLAUDE.md's own principle for this document: *"DocFit should not look larger than it really is. If
we have 500 providers, say 500. If we cover Long Beach, say Long Beach."*

## Snapshot (this phase, after the real bounded NPPES re-import)

| Metric | Count |
|---|---|
| Providers | 582 (271 organizations, 311 individuals) |
| Practice locations | 835 |
| Specialty categories | 19 |
| NUCC taxonomy codes mapped | 27 |
| Geography reference rows (`zip_geography`) | 6 |
| Counties represented | 1 (Los Angeles) |

## Geographic coverage: Long Beach-area only

**Actually loaded**: 6 ZIP codes in and immediately around Long Beach, California (90802, 90803,
90806, 90815, Lakewood 90712, Signal Hill 90755) -- all within Los Angeles County. This is the same
small demo footprint the product has had since the original TODAY-MVP phase; this phase did not
expand geographic coverage, only what's *findable* within that same footprint (specialty
breadth) and what's *ready* to expand it (architecture, documented below).

**Not loaded**: the rest of Los Angeles County (dozens more ZIPs), the rest of Southern California,
and the rest of California. No claim is made anywhere in the product about coverage beyond the 6
ZIPs above.

## Specialty coverage: real counts, this run

Distinct providers per specialty category, counted directly from the database (not location rows,
not taxonomy rows):

| Specialty | Providers |
|---|---|
| Psychiatry / Mental Health | 309 |
| Primary Care | 158 |
| Pediatrics | 41 |
| Physical Medicine & Rehabilitation | 18 |
| Cardiology | 12 |
| General Surgery | 12 |
| Ophthalmology | 11 |
| Orthopedics | 9 |
| Obstetrics & Gynecology | 9 |
| Urology | 7 |
| Dermatology | 6 |
| Neurology | 6 |
| Pulmonology | 5 |
| Nephrology | 4 |
| Gastroenterology | 3 |
| Endocrinology | 2 |
| Otolaryngology / ENT | 1 |
| Rheumatology | 1 |
| Allergy & Immunology | 1 |

13 of the 19 categories have at least 5 real providers within the current 6-ZIP footprint; 3
(Otolaryngology, Rheumatology, Allergy & Immunology) have exactly 1. This is an honest reflection
of a genuinely small demo dataset, not a data-quality problem -- these specialties are inherently
less common per capita than Primary Care or Psychiatry, and a 6-ZIP sample is small. A live
provider search for one of these categories within a tight radius may legitimately return zero or
one result today; the zero-result recovery UX (Care Discovery V3) handles this honestly rather
than hiding it.

## Readiness labels (CLAUDE.md "Honest Readiness Labels")

| | Architecture | Data actually loaded |
|---|---|---|
| **LA County** (all of it) | **YES** -- `NppesImportRunner` already fetches by ZIP + entity type + reads its taxonomy allowlist dynamically from `npi_taxonomy`; adding more LA County ZIPs to `zip_geography` and re-running the import needs zero code changes. | **NO** -- only 6 of LA County's ~300 ZIP codes are loaded. |
| **California** (statewide) | **PARTIAL** -- the same import mechanism works for any CA ZIP, but `zip_geography` has no bulk California ZCTA import wired up yet (see "If a larger import is ever needed," below); county reference data exists for exactly 1 county. | **NO**. |

Do not read "architecture: YES" as "ready to flip a switch with no further work" -- it means the
*code path* that would consume more ZIPs already exists and was proven this phase (the specialty
expansion's real re-import used the exact same mechanism), not that scaling to hundreds of ZIPs is
risk-free without also revisiting rate-limiting/pacing toward NPPES and reviewing performance at
that data volume (see `docs/geospatial-scaling.md`'s 10,000-row synthetic benchmark for a
realistic proxy of that scale, not the real thing).

## If a larger import is ever needed

1. **Geography first.** Populate `zip_geography` with a real ZCTA-based reference set for the
   target area (LA County or statewide) from the U.S. Census Bureau (`docs/provider-source-research.md`
   discusses Census as the right authoritative source; no bulk import of it was built this phase --
   `zip_geography` today is still the same 6 hand-curated demo rows, now with a `county` column
   added but not yet populated at scale).
2. **Run the NPPES importer** (`./mvnw spring-boot:run -Dspring-boot.run.profiles=import`) against
   the expanded ZIP set. No code change needed -- verified this phase by re-running the exact same
   importer after only adding new taxonomy codes, and watching it pick up 90 new providers with no
   changes to `NppesImportRunner` itself.
3. **Run the data quality report** (`ProviderDataQualityService`, runs automatically after every
   import) and review any `ERROR`-severity findings before considering the import "done."
4. **Re-measure search performance** at the new real row count -- do not assume the 10,000-row
   synthetic benchmark's numbers transfer exactly; re-run `EXPLAIN ANALYZE` against the real data.

## What this document is not

Not a claim about clinical quality, provider availability, or insurance participation -- purely a
factual count of what rows exist in the database. See `docs/insurance-network-architecture.md` for
why provider-data freshness and network-evidence freshness are tracked completely separately.
