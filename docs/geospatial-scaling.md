# Geospatial scaling — when Haversine-in-Java stops being enough

DocFit AI's provider search currently computes distance with a plain Haversine formula in Java
(`ProviderSearchService.haversineMiles`), applied to every `(provider, taxonomy-matched location)`
row returned by a single SQL join, then filtered/sorted in memory. This document records why
that's fine today, what the real bottleneck will be as the dataset grows, and what to change
first -- without introducing PostGIS before it's actually justified (CLAUDE.md 35).

**Update (release-candidate-hardening phase):** the bounding-box pre-filter this document
originally recommended as "the next real step" has been implemented (`ProviderSearchService`
MATCH_QUERY, migration V10). See "Bounding-box pre-filter: implemented and measured" below for
what it does and does not fix, backed by real `EXPLAIN ANALYZE` evidence at both current and
simulated scale.

## Current measured behavior

Against the real dataset produced by this phase's live NPPES import (492 providers, 689 practice
locations, spanning individual and organization providers across 6 demo ZIPs — see the final
report for the full import numbers):

```
EXPLAIN ANALYZE <core search join, PRIMARY_CARE taxonomy codes>
Execution Time: 1.267 ms
Planning Time: 8.172 ms
```

Measured end-to-end HTTP latency (`curl`, local Postgres, steady state after warmup):
- Search without a plan selected: ~25–32 ms
- Search with a plan selected (adds the batched network-evidence lookup): ~50–65 ms
- Provider detail: ~25–33 ms

Postgres chose sequential scans over `provider`, `provider_location`, and `provider_taxonomy`
for this query — not because indexes are missing, but because at ~500–700 rows a seq scan is
genuinely cheaper than an index scan, and the planner correctly says so. This is expected,
correct behavior at the current data scale, not a problem to fix.

## Where this breaks down

The current approach does two things that stop scaling gracefully as the provider count grows
into the tens or hundreds of thousands (LA County or California scale, CLAUDE.md 32):

1. **The taxonomy-code join has no geographic pre-filter.** Every row matching the requested
   specialty's taxonomy codes is pulled back from Postgres — nationwide, if the data were
   nationwide — and only then is distance computed and radius-filtered in Java. At small scale
   this is fine (the whole matching set fits comfortably in memory and the query plan above shows
   it costs about a millisecond). At large scale, this becomes "fetch tens of thousands of rows
   to throw most of them away."
2. **No bounding-box or spatial index exists on `provider_location(latitude, longitude)`.** A
   larger dataset needs the database to narrow candidates by rough geographic proximity *before*
   Java computes exact Haversine distance, not after.

## Bounding-box pre-filter: implemented and measured

`ProviderSearchService.search` now computes a superset lat/lng bounding box from the requested
origin and radius and adds it to `MATCH_QUERY`'s `WHERE` clause
(`pl.latitude BETWEEN :minLat AND :maxLat`, same for longitude), backed by a new B-tree index
(`V10__add_provider_location_geo_index.sql`, `provider_location(latitude, longitude)`). The exact
circle is still enforced afterward via the existing Haversine filter in Java -- the box only has to
be a superset, so results are unchanged; only the candidate set fetched from the database shrinks.

**What this fixes, measured:** the original concern was a common specialty pulling every matching
row *nationwide* into Java regardless of the requested radius. Verified directly with a 20,000-row
synthetic simulation (uniformly distributed across the contiguous US, all sharing one taxonomy
code, run inside a transaction and rolled back -- never committed to any database):

```
Old query (no geographic bound at all): 20,011 rows returned to the application layer
New query (bounding box added):            14 rows returned to the application layer
```

That's the fix working as intended -- the application no longer holds, Haversine-computes, and
sorts a nationwide result set for a common specialty; it only ever sees candidates already inside
the requested area.

**What this does *not* fully fix, measured honestly:** database-side execution time for that same
worst-case (one taxonomy code shared by every row) barely moved --  67ms unbounded vs 61ms bounded.
`EXPLAIN ANALYZE` shows why: with a taxonomy code this common, Postgres's planner still drives the
join from `provider_taxonomy` (20,011 matches) and probes `provider_location` once per match via
the existing `provider_id` index, applying the new lat/lng bounds as a post-lookup `Filter` rather
than restructuring the plan to scan the new geo index first. The new index measurably helps the
*payload size* problem; it does not by itself change the join order for a pathologically common
taxonomy code. At real NPPES data (no single taxonomy code is anywhere near "every provider
nationwide") this is expected to matter less than the synthetic worst case shows -- but it is not
proven to disappear, so it's flagged here rather than assumed away.

At the current real dataset (492 providers), this is moot either way: `EXPLAIN ANALYZE` shows
Postgres correctly prefers a sequential scan over `provider` regardless of the new index (see
"Current measured behavior" above) -- there simply isn't enough data yet for either plan to matter.

**Next step if this becomes a measured problem at real scale** (not preemptive): Postgres's
built-in `cube`/`earthdistance` extension (ships with core Postgres, no separate install) adds a
`ll_to_earth`/`earth_box` GiST index that supports genuine radius queries and gives the planner a
real reason to drive the join from geography first. Consider it if a future EXPLAIN ANALYZE against
real production-scale data (not this synthetic worst case) shows the plain B-tree bounding box
insufficient -- not before, per CLAUDE.md 35's "don't introduce PostGIS before it's justified," which
applies equally to `cube`/`earthdistance` as a smaller but still real operational addition.

## When PostGIS actually becomes worth it

PostGIS is justified once DocFit needs more than "nearest point within a radius" — e.g. drive-time
isochrones, polygon-based service areas, or genuinely nationwide data where even a bounding-box
pre-filter isn't selective enough without a real spatial index (`GEOGRAPHY` column + GiST index,
`ST_DWithin`). That's a real operational commitment (an extension to install and maintain, a new
column type, migration of existing lat/lng data into `geography(Point,4326)`), so it should be
adopted when a measured query — not a guess — shows the bounding-box approach isn't enough, not
preemptively. Nothing in this phase's measured numbers justifies it yet.

## 10,000-row realistic-distribution benchmark (data-expansion phase)

The 20,000-row benchmark above was a deliberate *worst case* (every row sharing one taxonomy
code, to stress-test the bounding-box fix). This phase ran a second, more realistic synthetic
benchmark: 10,000 providers/locations distributed across California's real lat/lng bounding box
(not one point), with a realistic taxonomy mix (Primary Care ~35% of rows down to Allergy &
Immunology ~0.3%, spanning all 19 specialty categories) -- entirely inside one transaction,
`ROLLBACK`ed at the end, never committed.

```
Common specialty (Primary Care), 25mi bounding box: 4.8ms execution, 145 rows -- Index Scan on
  idx_provider_location_lat_lng, not a sequential scan.
Rare specialty (Allergy & Immunology), 50mi bounding box: 6.0ms execution, 0 rows -- same index
  used correctly even for a low-selectivity taxonomy filter.
Name search (ILIKE '%...%' across first/last/organization name): 15.8ms execution -- a full
  sequential scan of all 10,000+ rows (Rows Removed by Filter: 10,571). Still fast in absolute
  terms, but this is the one query in this benchmark that scales linearly with table size rather
  than being bounded by an index -- exactly the CLAUDE.md 63 concern. Extrapolating linearly (not
  measured beyond 10k this phase): roughly 80ms at 50k, 160ms at 100k. Still likely tolerable, but
  this is the first thing to re-measure and consider `pg_trgm` for if the provider count grows
  meaningfully past this phase's data (CLAUDE.md 64: only if measured, not preemptively -- adding
  a Postgres extension is a real operational commitment).
Provider detail (single NPI lookup, locations + taxonomies): 0.257ms -- fully indexed, trivial.
```

**Confirms the bounding-box index design decision.** At the small real dataset (492 rows), the
planner correctly preferred a sequential scan (see "Current measured behavior" above) -- there
wasn't enough data for the index to matter. At 10,000 rows, the planner now correctly *switches*
to using `idx_provider_location_lat_lng` via an Index Scan for both the common and rare specialty
case. This is the crossover point the original bounding-box work was built for, now observed
directly rather than assumed.

## 50,000- and 100,000-row benchmarks (LA County Expansion V5.1)

Run this phase, same rolled-back-transaction methodology as the 10,000-row benchmark above, but
with a specialty-mix distribution drawn from **this phase's own real measured LA County import**
(`docs/la-county-provider-import.md`) rather than a guess -- Psychiatry/Mental Health ~53% down to
Allergy & Immunology ~0.3% of synthetic rows (normalized relative weights, since a real provider can
carry more than one specialty so the raw per-specialty counts don't sum to the provider total).
Against the real dev database (already carrying the 5,854 real providers imported this phase), so
these numbers reflect real-plus-synthetic combined table size (~55,854 and ~105,854 respectively):

```
                          |    50k run    |   100k run
Common specialty (Primary Care), 25mi box  |  82.4ms       |  127.3ms
Rare specialty (Allergy & Immunology), 50mi|  2.2ms        |  4.6ms
Name search (ILIKE, full sequential scan)  |  66.0ms       |  144.4ms
Provider detail (single NPI lookup)        |  0.31ms       |  0.28ms
```

**Query plan behavior, honestly reported (a real finding, not the same shape as the 10,000-row
result)**: at this larger scale, the planner chose a **sequential scan on `provider_location`**
for the common-specialty query (not an Index Scan on `idx_provider_location_lat_lng` as at 10,000
rows) -- because the taxonomy filter (`provider_taxonomy` via `idx_provider_taxonomy_taxonomy_code`)
already narrows the candidate set enough first that a hash join against a full `provider_location`
scan is cheaper than a location-index lookup per candidate. This is the query planner making a
different, still-correct cost-based choice at a different data shape -- not a regression, and still
well under 100ms even at 100k rows for the more selective case, and ~127ms for the least selective
realistic case (Primary Care, the single most common specialty).

**Name search scales roughly linearly with table size, as predicted.** 66.0ms at 50k -> 144.4ms at
100k (a real measurement, not the 10,000-row benchmark's linear extrapolation, which predicted
~80ms/~160ms -- the real numbers came in slightly better than that extrapolation). This confirms
CLAUDE.md's own concern: this is the one query in the whole benchmark that doesn't benefit from a
targeted index and will keep growing with table size.

**`pg_trgm` decision: still deferred, evidence-based.** Even at 100k rows (roughly 18x DocFit's
real current 5,854-provider dataset), name search stays at 144ms -- noticeable in a strict sense,
but not "clearly, measurably slow" by any normal UX bar for a full search-endpoint round trip, and
nowhere close to a level that would justify adopting a new Postgres extension pre-emptively
(CLAUDE.md 64: "only if measured, not preemptively"). This is the same conclusion the 10,000-row
benchmark reached, now backed by real 50k/100k evidence instead of extrapolation. Revisit this
specific query if DocFit's real provider count ever grows into this range -- LA County's real
provider population, even fully imported, is very unlikely to reach 100k in DocFit's own database
(that would require importing essentially the county's entire physician population at once).

**Safety**: both runs completed entirely inside `BEGIN`/`ROLLBACK` against the real dev database --
never committed, never left any synthetic row behind. A 250,000-row run was not attempted this
phase (not required once 100k's results were unambiguous and consistent with the 50k trend; time
was better spent on the phase's other open items).

## 10,000-record CSV import throughput benchmark (LA County Expansion V5.1)

Unlike the row-count benchmarks above (rolled back, read-only measurement), throughput has to be
measured against a real committed import -- `ProviderCsvImportRunner` commits one small transaction
per row via `ProviderUpsertService`, so there's no single wrapping transaction to roll back. Run
against the real dev database with a disposable, clearly-prefixed synthetic NPI range
(`99xxxxxxxx`, verified to collide with nothing beforehand), then fully deleted afterward
(`provider_taxonomy`/`provider_location`/`provider` rows by NPI prefix, plus the two `data_import`
rows the runs created) -- verified back to the exact pre-benchmark baseline (5,854 providers, 8,095
locations) afterward.

10,000 synthetic rows, realistic specialty-mix weights (same distribution as the 50k/100k
benchmarks above), all sharing one of the 6 original Long Beach-area ZIPs:

| Run | Records | Result | Duration | Throughput |
|---|---|---|---|---|
| Create pass | 10,000 | 10,000 created, 0 updated, 0 failed | 139.0s | ~71.9 records/sec |
| Repeat pass (idempotent) | 10,000 | 0 created, 10,000 updated, 0 failed | 73.1s | ~136.8 records/sec |

**The repeat pass is meaningfully faster** (73s vs. 139s) -- an `UPDATE` against an existing row
(matched via the `uq_provider_location_normalized` unique index) skips the extra work a fresh
`INSERT` does (unique-constraint checks against a growing table, index maintenance for a brand new
row). This is a real, measured confirmation that the idempotent-upsert design doesn't get
progressively more expensive on repeated imports of unchanged data -- if anything, the opposite.

**No memory pressure or degradation observed** -- both runs completed in roughly the time attempted
above with a normal-sized JVM heap (no `-Xmx` tuning applied), consistent with the "one small
transaction per row, never load the whole file into memory" design (`docs/provider-ingestion.md`).

**An earlier attempt at this benchmark used a synthetic NPI format with letters** (`CBxxxxxxxx`),
which correctly failed `ProviderDataQualityService`'s `invalidNpiFormat` check for all 10,000 rows
(a real, working check catching genuinely malformed NPIs, not a bug) -- the benchmark was re-run
with a valid 10-digit-numeric synthetic range instead. Left in this document as a small honest note
that the first attempt wasn't discarded silently.

## Indexes added, and why

`provider_location(provider_id)`, `provider_location(postal_code)` -- support real queries the
provider-data-platform phase's services run (fetching a provider's locations; ZIP-based reference
lookups). `provider_location(latitude, longitude)` -- added this phase (V10) to support the new
bounding-box pre-filter above; see that section for measured impact and its current limits.
