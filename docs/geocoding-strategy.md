# Geocoding strategy

Research and architecture only this phase (CLAUDE.md "Geocoding Implementation": "Only implement
address geocoding if... official/free source is appropriate... Otherwise: architecture/
documentation only"). No address-level geocoding is wired up yet -- every coordinate DocFit AI
currently produces is a `ZIP_CENTROID`, truthfully labeled as such (`docs/map-and-location-accuracy.md`).

## Current state

`NppesImportRunner` geocodes by looking up a provider location's postal code in `zip_geography`
and using that ZIP's centroid latitude/longitude. This is honest (never `EXACT` or
`ADDRESS_GEOCODE`) but genuinely imprecise -- every provider at the same ZIP shares one coordinate.

## Can the Census geocoder help?

**Yes, and it's the right choice if/when this is implemented.** The U.S. Census Bureau's
Geocoding Services API (`geocoding.geo.census.gov`):

- Is **free**, publicly documented, and requires **no API key**.
- Supports **batch geocoding**: up to 10,000 addresses per submitted file in one request --
  exactly the shape needed to geocode a bounded provider import, not a live per-search-request
  call.
- Is authoritative for **U.S. addresses only** (a real, acceptable constraint -- DocFit AI only
  serves U.S. addresses today).
- Returns a **census-block-level match** with matched coordinates when it finds a confident match,
  and explicitly reports non-matches rather than guessing -- exactly the "graceful failure" shape
  this architecture needs (see "On failure," below).

No documented hard rate limit was found for the batch endpoint beyond the 10,000-per-file batch
cap; parallel requests are anecdotally reported to sustain a few thousand addresses per minute
without hitting a cap, though DocFit AI would not rely on an undocumented number as a real budget
(same posture as `docs/provider-source-research.md`'s NPPES section) -- a future implementation
should self-impose pacing regardless.

## Could provider addresses be batch-geocoded?

Yes, structurally: after an NPPES/CSV import populates `provider_location` rows with
`coordinate_precision = ZIP_CENTROID`, a **separate, operator-triggered maintenance step** could:

1. Select locations still at `ZIP_CENTROID` (or never-yet-geocoded).
2. Batch them (≤10,000 per Census API call) into the Census batch geocoder.
3. For each confident match, update `latitude`/`longitude` and set
   `coordinate_precision = ADDRESS_GEOCODE`.
4. For each non-match, leave the row at `ZIP_CENTROID` -- never invent a coordinate.

This is deliberately **not** part of the search request path (CLAUDE.md "Geocoding Cache":
"Geocoding belongs in ingestion/maintenance pipeline, not provider-search request path") -- it
would run as its own bounded, operator-triggered job, the same posture as the NPPES/CSV importers
themselves.

## What precision can be stored?

DocFit AI's existing `CoordinatePrecision` enum already has the right shape for this:
`ADDRESS_GEOCODE`, `ZIP_CENTROID`, `CITY_CENTROID`, `UNKNOWN` -- no schema change would be needed.
A Census-geocoder match would be stored as `ADDRESS_GEOCODE`; `EXACT` is reserved for a source that
genuinely asserts surveyed/exact coordinates, which Census's block-level match is not quite (it is
a real address-level geocode, materially better than a ZIP centroid, but still a computed match
against reference geography, not a GPS survey point) -- so `ADDRESS_GEOCODE` is the honest label,
not `EXACT`.

## What happens on failure?

- **No match found**: the location keeps its existing `ZIP_CENTROID` coordinate and precision --
  never a fabricated address-level coordinate (CLAUDE.md "Geocoding Failure": "fallback to ZIP
  centroid where available... do not invent coordinates").
- **API unreachable / batch request fails**: the maintenance job logs and stops (or retries with
  the same bounded-retry posture as `NppesClient`); it never partially commits a batch's results in
  a way that could mismatch addresses to coordinates.
- **A location genuinely has no valid postal code** (shouldn't happen given upstream validation,
  but defensively): `coordinate_precision = UNKNOWN`, never guessed.

## Caching

If implemented, results would be cached by **normalized address**, not re-geocoded on every
maintenance run -- most provider addresses don't change between imports, so re-sending an
already-successfully-geocoded address to the Census API on every run would be wasted work and
wasted goodwill toward a free public service. A `source_last_updated_at`-style check (already the
pattern `provider_location` uses for other provenance) is enough; no new caching infrastructure
(e.g. Redis) would be needed for this.

## Why not implemented this phase

This phase's priority was specialty and geography *architecture* readiness plus real, bounded data
coverage verification (`docs/data-coverage.md`) -- adding a second external-API integration
(beyond NPPES) for address-level geocoding was judged a distinct, separately-scoped follow-up
rather than something to rush into the same phase. The architecture above is deliberately concrete
enough that a future phase can implement it directly against this plan rather than re-researching
it.
