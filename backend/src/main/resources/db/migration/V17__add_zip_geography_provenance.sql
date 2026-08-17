-- Geography provenance (CLAUDE.md "Geography Provenance"): where did these coordinates come
-- from, which source/version, when imported. Nullable at the schema level (a hand-inserted row
-- doesn't strictly require it), but every row this phase populates does set it.
ALTER TABLE zip_geography ADD COLUMN source_name VARCHAR(200);
ALTER TABLE zip_geography ADD COLUMN source_version VARCHAR(50);
ALTER TABLE zip_geography ADD COLUMN source_imported_at TIMESTAMPTZ;

-- Backfill the 6 pre-existing hand-curated demo rows honestly -- they were never sourced from an
-- official bulk file, so their provenance says exactly that rather than fabricating an official
-- source retroactively.
UPDATE zip_geography
SET source_name = 'DocFit AI hand-curated demo seed (V3 migration)',
    source_version = 'v1',
    source_imported_at = now()
WHERE source_name IS NULL;
