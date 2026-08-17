# Geocoding strategy

**Implemented this phase** (LA County Expansion V5.1) -- an operator-controlled, bounded address
geocoding pipeline now exists (`com.docfitai.backend.provider.geocoding`). This document previously
described architecture/research only; it's now updated to describe the real implementation, what
was verified empirically, and what remains deliberately deferred.

## Empirical research (this phase)

WebFetch could not render the official Census Geocoder documentation pages (the same JS-rendering
issue encountered with the NPPES docs in an earlier phase), so behavior was confirmed by direct
requests against the live public API instead of trusted from documentation alone:

- **`https://geocoding.geo.census.gov/geocoder/locations/address`** (structured street/city/state/
  zip params) is free, requires **no API key**, and returned a correct real match for a known real
  address (`4600 Silver Hill Rd, Washington, DC 20233` -> `38.845053, -76.928366`, matching the
  Census Bureau's own headquarters).
- A genuine no-match (a deliberately nonexistent address) returns **HTTP 200 with an empty
  `addressMatches` array** -- not an error. A malformed/gibberish street value against a real city/
  state/zip behaves the same way (empty array, not a 4xx).
- A **missing required parameter** (e.g. no `street`) returns **HTTP 400**.
- `benchmark=Public_AR_Current` is the Bureau's own current default (confirmed via
  `/geocoder/benchmarks`), used here rather than a pinned historical vintage.
- No documented numeric rate limit was found (same finding as NPPES) -- the client's own bounded
  timeout/retry (10s timeout, 3 attempts, matching `NppesClient`'s posture) is a courtesy to the
  source, not a response to a specific limit.
- A **batch endpoint** (`/geocoder/locations/addressbatch`, CSV file upload, historically documented
  as supporting up to 10,000 addresses per file) also exists and was not used this phase -- see
  "Why the single-address endpoint, not batch," below.

## What was implemented

- **`CensusGeocoderClient`** -- thin HTTP client for the structured single-address endpoint,
  bounded timeout/retry, never retries a 4xx.
- **`CensusGeocoderResponseParser`** -- pure, unit-tested JSON parsing (`x`=longitude,
  `y`=latitude, verified empirically) into a `GeocodeResult` (`Matched`/`NoMatch`/`Failed` --
  deliberately distinct types, since a failure is retriable/transient and a genuine no-match is
  not, and the two must never be treated the same way).
- **`AddressGeocodeCache`** (`address_geocode_cache` table, V19 migration) -- keyed by the exact
  same normalized-address function (`LocationNormalizer.normalizedKey`) `provider_location` already
  uses for its own dedup, so an unchanged address is never re-geocoded on a later run. Only
  `MATCHED`/`NO_MATCH` outcomes are cached -- a `FAILED` outcome (timeout, transient HTTP error) is
  deliberately **not** cached, so it gets retried on the next run rather than being stuck failed
  forever.
- **`ProviderGeocodingService`** -- the pipeline: selects up to `maxRecords` (hard ceiling 2,000
  per run) `provider_location` rows still at `ZIP_CENTROID` precision, checks the cache first, calls
  the client on a cache miss, and on a real match calls the new
  `ProviderLocation.upgradeToAddressGeocode(lat, lng)` method -- which deliberately touches **only**
  latitude/longitude/precision, never phone/fax, unlike the existing `updateFrom(...)` re-import
  method. A no-match or failure **never modifies existing coordinates** -- the ZIP centroid is
  retained, exactly as CLAUDE.md's "do not invent coordinates" principle requires.
- **`GeocodingRunner`** -- operator CLI entry point (`docfitai.geocode.enabled`, default `false`;
  see `docs/operations-runbook.md`). **Never called from `GET /api/providers/search`** or any other
  request path -- ingestion/maintenance only, matching the exact requirement from the original
  architecture doc.
- Guarded by `ProductionSafetyValidator` the same way CSV/geography import are -- refuses to start
  the `prod` profile with `docfitai.geocode.enabled` left on.

## Why the single-address endpoint, not batch

The batch endpoint requires a CSV-file multipart upload and returns a pipe-delimited response
format, meaningfully more implementation complexity than the structured single-address GET request.
Given this phase's per-run record cap (2,000, itself well under the batch endpoint's 10,000-per-file
limit) and the existing cache avoiding repeat work across runs, the simpler single-address endpoint
was judged sufficient for LA-County-scale usage. **The batch endpoint remains the right choice for a
much larger future run** (e.g. a one-time statewide California geocode sweep) -- documented as a
future option, not implemented, consistent with this codebase's "defer, don't rush" posture toward
California work (`docs/geospatial-scaling.md`).

## Precision labeling

Unchanged from the original architecture: a Census structured-address match is stored as
`ADDRESS_GEOCODE`, never `EXACT` (reserved for a source that genuinely asserts surveyed/exact
coordinates) -- the pipeline only ever takes the first returned match and does not attempt to
disambiguate multiple candidate matches, so it never claims more confidence than the source
actually provided.

## Testing (CI never calls the live API)

- `CensusGeocoderResponseParserTest` -- pure unit tests against real response bodies captured this
  phase (a strong match, a genuine no-match, malformed/empty JSON) -- no HTTP involved.
- `ProviderGeocodingServiceTest` -- exercises the full pipeline against a mocked
  `CensusGeocoderClient` (same Mockito pattern as `ProviderRefreshServiceTest`'s mocked
  `NppesClient`): a real match upgrades precision and coordinates, a no-match retains the ZIP
  centroid, a failure retains coordinates and is not cached (so it's retried next run), and a
  cached outcome is reused without a second client call.

## Caching

Implemented as described in the original architecture doc: cached by normalized address
(`address_geocode_cache`), not by a timestamp-based staleness check -- an address that hasn't
changed is never re-geocoded, full stop, since NPPES doesn't report an "address changed" signal
finer-grained than the whole location record changing (which would already produce a different
normalized key).

## What remains deferred

- **Batch endpoint** for statewide-scale geocoding (see above).
- **Multiple-candidate disambiguation** -- if the Census API ever returns more than one match, this
  pipeline takes the first and does not attempt smarter disambiguation (e.g. picking the match
  closest to the existing ZIP centroid). Not needed yet -- empirically, every real address tested
  this phase returned zero or one match.
- **Automatic re-geocoding on address change** -- currently, a changed `provider_location` row (new
  normalized key) is simply treated as a new `ZIP_CENTROID` candidate on the next geocoding run,
  the same as any other never-yet-geocoded row. No special "this address changed, re-geocode it
  urgently" fast path exists.
