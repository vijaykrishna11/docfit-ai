-- Demo ZIP geography for the Long Beach / nearby Los Angeles County area only.
-- Source: U.S. Census Bureau ZCTA centroid data, cross-referenced against
-- public ZIP reference aggregators (coordinates rounded to Census
-- Gazetteer-style precision). This is intentionally a small, hand-picked
-- subset for the one-day demo and the next-step provider import -- not the
-- full 1,808-row California ZCTA set.

INSERT INTO zip_geography (zip_code, city, state_code, latitude, longitude) VALUES
    ('90802', 'Long Beach',  'CA', 33.770000, -118.191000),
    ('90803', 'Long Beach',  'CA', 33.759000, -118.126000),
    ('90806', 'Long Beach',  'CA', 33.806000, -118.182000),
    ('90815', 'Long Beach',  'CA', 33.794000, -118.116000),
    ('90712', 'Lakewood',    'CA', 33.849000, -118.147000),
    ('90755', 'Signal Hill', 'CA', 33.803000, -118.168000);
