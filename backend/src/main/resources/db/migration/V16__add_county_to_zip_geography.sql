-- County reference field (CLAUDE.md "County": "Add county field only if authoritative source
-- supports it reliably... Do not infer county from city name"). Nullable at the schema level --
-- a future geography row is not required to supply one -- but every row currently in this table
-- is backfilled here because all 6 are well-established incorporated cities within Los Angeles
-- County, California (Long Beach, Lakewood, Signal Hill), matching this table's own existing
-- seed comment ("Demo ZIP geography for the Long Beach / nearby Los Angeles County area").
ALTER TABLE zip_geography ADD COLUMN county VARCHAR(100);

UPDATE zip_geography SET county = 'Los Angeles' WHERE state_code = 'CA';
