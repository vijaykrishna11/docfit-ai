# LA County geography sources

Where the `zip_geography` reference data imported this phase actually comes from, what it does
and does not claim, and the honest limitations of how it was derived. Companion docs:
`docs/data-coverage.md` (what's actually loaded into the DB right now), `docs/geospatial-scaling.md`
(performance at scale), `docs/la-county-provider-import.md` (the separate question of which
*providers* were imported into this geography).

## Why Census, not a ZIP-code website

CLAUDE.md's own instruction for this phase was to use "CURRENT authoritative primary geography
sources" and explicitly reject "random ZIP websites, scraped Google data, or unverified GitHub
lists." The U.S. Census Bureau is the authoritative federal source for both ZCTA boundaries/
centroids and their relationship to counties and incorporated places, and publishes the underlying
files directly and openly. All three files below were downloaded directly via `curl` from
`www2.census.gov` (Census's own file server), not scraped from a rendered webpage, a
third-party mirror, or an LLM's prior knowledge of "common LA ZIP codes."

## The three source files

| File | URL | Published | Format |
|---|---|---|---|
| 2024 ZCTA Gazetteer (national) | `https://www2.census.gov/geo/docs/maps-data/data/gazetteer/2024_Gazetteer/2024_Gaz_zcta_national.zip` | 2024 vintage, Census Gazetteer Files program | Tab-delimited, 33,792 national ZCTA rows |
| 2020 ZCTA-to-County relationship file | `https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_county20_natl.txt` | 2020 Census, Relationship Files program | Pipe-delimited, 47,864 rows |
| 2020 ZCTA-to-Place relationship file | `https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_place20_natl.txt` | 2020 Census, Relationship Files program | Pipe-delimited |

**Licensing**: U.S. Census Bureau data is public domain (U.S. federal government work product) and
not subject to copyright restriction. No license key, account, or attribution requirement applies
beyond standard good practice (citing the Bureau as the source, which this document does).

**Fields used**:
- Gazetteer file: `GEOID` (5-digit ZCTA code), `INTPTLAT`/`INTPTLONG` (the ZCTA's internal point --
  a Census-computed centroid guaranteed to fall within the ZCTA's boundary, not necessarily its
  exact geometric center).
- County relationship file: `GEOID_ZCTA5_20`, `GEOID_COUNTY_20` (5-digit FIPS; LA County is
  `06037`), `NAMELSAD_COUNTY_20`, `AREALAND_PART` (land area, in square meters, of the specific
  ZCTA-county intersection -- used to pick the single *primary* county for a ZCTA that spans more
  than one).
- Place relationship file: same ZCTA/area-part structure, plus `NAMELSAD_PLACE_20` (the Census
  Place name, e.g. "Los Angeles city," "Long Beach city," "Florence-Graham CDP") -- used the same
  way, to pick the single primary place per ZCTA.

## ZIP vs. ZCTA: an honest distinction

**A USPS ZIP code and a Census ZCTA (ZIP Code Tabulation Area) are not the same thing**, and this
dataset is built entirely from ZCTAs, not from USPS's own ZIP database (which the Census Bureau
does not publish and which requires a commercial license from a reseller to obtain in bulk).

- ZIP codes are USPS mail-routing constructs that can change at USPS's discretion, sometimes
  represent a single large building or PO box cluster rather than an area, and have no officially
  published boundary at all.
- ZCTAs are the Census Bureau's own statistical approximation of ZIP code service areas, built by
  aggregating census blocks by their most common ZIP code, redrawn once per decennial census (the
  current vintage is 2020).

In practice the two overlap heavily for most residential 5-digit codes, and this is the standard
tradeoff every geography-based product without a commercial USPS data license makes. But it means:
a handful of real USPS ZIP codes (typically PO-box-only or single-large-employer codes) have no
corresponding ZCTA and will never appear in this dataset, and the "boundary" implied by a ZCTA
centroid is a statistical approximation, not a legal ZIP boundary. `zip_geography.zip_code` should
be read as "ZCTA, used as a practical stand-in for a ZIP code" throughout this codebase -- it is
not claimed to be a licensed, authoritative USPS ZIP database.

## How the 295-row dataset was built

`build_la_county_zips.py` (a one-off ETL script, not part of the deployed application) joined the
three files as follows:

1. **County assignment**: for every ZCTA, pick the county with the largest `AREALAND_PART` (its
   primary county) and keep only ZCTAs whose primary county is LA County (FIPS `06037`). This
   correctly excludes ZCTAs that only clip a small corner of LA County but are primarily in an
   adjacent county (e.g. parts of Orange or Ventura County), and correctly includes ZCTAs that
   span into an adjacent county but are majority-LA-County.
2. **City/place assignment**: for each LA-County ZCTA, pick the Census place with the largest
   `AREALAND_PART` as its primary/display city, stripping the Census Legal/Statistical Area
   Description suffix (" city," " CDP," " town," " village," " borough") for a clean display name
   (e.g. "Florence-Graham CDP" -> "Florence-Graham").
3. **Coordinates**: the ZCTA's Gazetteer internal-point lat/lng, used as this row's centroid.
4. **Keep only ZCTAs with all three resolved** (county + place + coordinates). 295 of the LA-County
   ZCTAs had all three; a small number were dropped for missing one of the three joins entirely.

## Known, honest limitation: primary-place selection for unincorporated areas

**30 of the 295 rows have no resolved city name** (empty `city` field): `90073`, `90265`, `90601`,
`90704`, `91042`, `91301`, `91302`, `91311`, `91342`, `91381`, `91384`, `91387`, `91390`, `91608`,
`91702`, `91724`, `91750`, `91759`, `92397`, `93510`, `93532`, `93535`, `93536`, `93543`, `93544`,
`93550`, `93552`, `93553`, `93563`, `93591`.

This is not a data-loading bug -- it is what "pick the primary place by largest land area" honestly
produces for a ZCTA whose majority land area is *not* inside any incorporated city or Census
Designated Place (largely Antelope Valley / San Gabriel Mountains ZCTAs, plus a few dense-urban
ZCTAs where the majority-area "place" happens to be recorded as unincorporated county land in the
relationship file even though a well-known city name is commonly associated with the ZIP, e.g.
`90265` Malibu or `91311` Chatsworth). Rather than guess a display name for these rows, the ETL
script and this dataset leave `city` genuinely empty, and `GeographyRecordParser` treats `county`
(and by the same principle, `city`) as an optional field rather than fabricating a value. Any UI
consuming `zip_geography.city` must handle an empty city honestly (fall back to ZIP + county, never
silently show a wrong or guessed city name).

## What this dataset is not

- Not a claim of USPS ZIP boundary accuracy (see "ZIP vs. ZCTA" above).
- Not a claim that every LA County ZIP/ZCTA in official use is present -- 295 rows is what the
  county+place+coordinate join produced from the specific vintages used (2024 Gazetteer, 2020
  relationship files); a handful of edge cases (brand-new ZCTAs since 2020, PO-box-only codes) may
  be genuinely absent.
- Not provider data. Loading this reference geography does **not** mean DocFit has provider records
  for all 295 ZIPs -- see `docs/la-county-provider-import.md` and `docs/data-coverage.md` for what
  provider data actually exists against this geography.
