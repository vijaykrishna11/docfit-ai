# Geospatial scaling — when Haversine-in-Java stops being enough

DocFit AI's provider search currently computes distance with a plain Haversine formula in Java
(`ProviderSearchService.haversineMiles`), applied to every `(provider, taxonomy-matched location)`
row returned by a single SQL join, then filtered/sorted in memory. This document records why
that's fine today, what the real bottleneck will be as the dataset grows, and what to change
first -- without introducing PostGIS before it's actually justified (CLAUDE.md 35).

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

## What to change first (before PostGIS)

A `provider_location(state_code)` / `provider_location(postal_code)` filter is already available
and already used for radius pre-filtering in a de facto way (`ProviderSearchService` narrows by
taxonomy first, and DocFit's data is currently geographically clustered around a handful of demo
ZIPs, so this hasn't mattered yet). The next real step, before any new extension:

1. **Add a lat/lng bounding-box pre-filter in SQL.** Given a search origin and radius, compute a
   simple bounding box (`latitude BETWEEN ... AND ...`, `longitude BETWEEN ... AND ...`) and add
   it to the `WHERE` clause alongside the taxonomy filter, with a plain B-tree index on
   `provider_location(latitude, longitude)`. This is cheap, requires no new Postgres extension,
   and eliminates the vast majority of geographically-irrelevant rows before they ever reach Java
   — the same idea PostGIS's `ST_DWithin` uses, just without the spatial index machinery.
2. **Consider Postgres's built-in `cube`/`earthdistance` extension** (ships with core Postgres,
   no separate install) if bounding-box + Haversine-in-Java ever shows up as a measured
   bottleneck. It adds a `ll_to_earth`/`earth_box` GiST index for genuine radius queries in SQL,
   without the operational weight of PostGIS.

## When PostGIS actually becomes worth it

PostGIS is justified once DocFit needs more than "nearest point within a radius" — e.g. drive-time
isochrones, polygon-based service areas, or genuinely nationwide data where even a bounding-box
pre-filter isn't selective enough without a real spatial index (`GEOGRAPHY` column + GiST index,
`ST_DWithin`). That's a real operational commitment (an extension to install and maintain, a new
column type, migration of existing lat/lng data into `geography(Point,4326)`), so it should be
adopted when a measured query — not a guess — shows the bounding-box approach isn't enough, not
preemptively. Nothing in this phase's measured numbers justifies it yet.

## Indexes added this phase, and why

`provider_location(provider_id)`, `provider_location(postal_code)` — both support real queries
this phase's services run (fetching a provider's locations; ZIP-based reference lookups). No
lat/lng index was added yet, per the above — there's no bounding-box query to support it until
the next phase implements one, and adding an index nothing queries yet would be exactly the kind
of unjustified index CLAUDE.md 34/91 warns against.
