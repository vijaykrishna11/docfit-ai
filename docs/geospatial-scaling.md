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

**Not attempted this phase**: 50,000- and 100,000-row benchmarks. The 10,000-row result above was
judged sufficient evidence for this phase's scope (LA County readiness, not yet California-wide);
a larger synthetic run is a reasonable next step before a genuinely large real import, not before.

## Indexes added, and why

`provider_location(provider_id)`, `provider_location(postal_code)` -- support real queries the
provider-data-platform phase's services run (fetching a provider's locations; ZIP-based reference
lookups). `provider_location(latitude, longitude)` -- added this phase (V10) to support the new
bounding-box pre-filter above; see that section for measured impact and its current limits.
