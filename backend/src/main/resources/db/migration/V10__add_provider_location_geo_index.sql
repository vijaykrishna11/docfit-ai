-- Supports the bounding-box pre-filter in ProviderSearchService.MATCH_QUERY (added alongside
-- this migration): without any index, a specialty search scanned every provider_location row
-- nationwide before filtering by distance in Java. A plain composite B-tree index isn't as
-- precise as a spatial index (GiST) for an arbitrary 2D box, but it lets Postgres use the
-- latitude range to prune the scan, which is a real improvement with no new dependency --
-- consistent with the existing "no PostGIS unless justified" decision (see
-- docs/geospatial-scaling.md). Revisit if EXPLAIN ANALYZE at real data volume shows it isn't
-- enough.
CREATE INDEX idx_provider_location_lat_lng ON provider_location (latitude, longitude);
