-- City is not always resolvable for a real, legitimate LA County ZCTA (CLAUDE.md "City
-- Representation Limitations"): a ZCTA whose majority land area isn't inside any incorporated
-- place or Census Designated Place -- largely Antelope Valley / San Gabriel Mountains ZCTAs, per
-- docs/la-county-geography-sources.md -- has no honest primary city to report. Relaxing this
-- constraint rather than fabricating a display name, matching the identical precedent set by V8
-- for provider.city ("ALTER TABLE provider ALTER COLUMN city DROP NOT NULL").
ALTER TABLE zip_geography ALTER COLUMN city DROP NOT NULL;
