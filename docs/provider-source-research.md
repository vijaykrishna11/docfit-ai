# Provider source research

Exact source decisions for DocFit AI's provider identity data. Companion docs:
`docs/provider-ingestion.md` (how the importers use these sources),
`docs/specialty-taxonomy-map.md` (the taxonomy code set), `docs/geocoding-strategy.md`
(location precision).

## Authoritative source: NPPES / the NPI Registry

DocFit AI's provider identity (NPI, name, entity type, practice address, taxonomy) comes from the
**NPI Registry public API** (`https://npiregistry.cms.hhs.gov/api/`), the public-facing search
interface over NPPES (National Plan and Provider Enumeration System), operated by CMS. This was
the source before this phase and remains the source after it -- this phase's research reconfirmed
it, rather than replacing it.

- **Free, public, no API key.** No credentials or registration are required to query it.
- **Official.** It's a CMS-operated system, the canonical source NPIs are issued from -- not a
  scrape or a mirror.
- **Supports the exact query shape DocFit AI needs**: postal-code search, `enumeration_type`
  filtering (`NPI-1` individual / `NPI-2` organization), and a per-request `limit`. `NppesClient`
  uses `version=2.1`.
- **No published numeric rate limit was found.** Multiple secondary sources describe it as
  intended for "light, occasional lookups" without a documented request-per-second ceiling. Given
  the absence of a hard published number, DocFit AI does not try to push against an unknown limit
  -- the importer only ever queries a small, operator-controlled set of ZIPs (6 in the current
  demo dataset), and `NppesClient` now has a bounded timeout (15s per request, 10s connect) and a
  small retry budget (3 attempts, transient/5xx failures only, never a 4xx) as a courtesy to the
  source rather than a response to a specific documented limit.
- **No terms-of-use restriction found that would block DocFit AI's use** (public directory lookup
  for a healthcare navigation product) -- the data itself is a public directory CMS already
  publishes for exactly this kind of consumption.

## What NPPES's own `practiceLocations` field gives DocFit AI for free

A provider's NPPES record can include, beyond its primary address, a `practiceLocations` array --
genuine additional real-world offices reported by the provider to NPPES. `NppesProviderMapper`
already consumes this field (verified against real data: one organization, "SAMEDAY DOCTORS,
P.C.", has 41 real practice locations reported this way). This is why DocFit AI's multi-location
architecture is backed by real source data, not a synthetic stand-in.

## Alternatives considered and rejected

- **Bulk NPPES monthly/weekly downloadable files.** CMS also publishes full national bulk files
  (multi-gigabyte CSVs). Rejected for this phase's default workflow: CLAUDE.md's "Do Not
  Auto-Download Nationwide NPPES" explicitly rules out application startup depending on this, and
  a full national file is far more data than any current DocFit AI deployment needs. The bounded,
  ZIP-targeted API approach stays proportional to actual demand. The bulk-file path remains a
  reasonable *future* option for a genuinely large-scale, one-time seed (see
  `docs/data-coverage.md`, "If a larger import is ever needed") -- documented as an option, not
  implemented.
- **Scraped provider-directory websites** (Healthgrades, Zocdoc, insurer directories, Google
  Business listings). Explicitly rejected per CLAUDE.md's source-priority rule. These are not
  authoritative for provider *identity* (NPI, legal name, entity type) the way NPPES is, carry
  real terms-of-service risk, and would reintroduce exactly the kind of unverified, stale,
  inconsistent data DocFit AI's whole provenance model exists to avoid.
- **Commercial NPI-lookup aggregator APIs** (e.g. npiprofile.com-style services). These mirror the
  same underlying NPPES data, sometimes with a nicer API shape, but add a paid/rate-limited
  middleman with its own terms for data DocFit AI can already get for free, directly, from the
  authoritative source. Not adopted.

## Taxonomy source

See `docs/specialty-taxonomy-map.md` for the full record. Short version: the **NUCC Health Care
Provider Taxonomy Code Set**, cross-checked this phase against the CMS Medicare taxonomy crosswalk
and NUCC's own published version PDFs for every new code added.

## Geography reference source

See `docs/geocoding-strategy.md`. DocFit AI's `zip_geography` table today is a small, hand-curated
set of demo ZIPs (6 rows) with centroid coordinates -- not yet backed by a bulk authoritative
geography import. That document covers the research into Census/ZCTA as the right source for
scaling this, and why it wasn't bulk-imported this phase.
