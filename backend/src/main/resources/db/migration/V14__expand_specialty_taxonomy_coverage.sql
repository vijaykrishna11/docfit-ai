-- Specialty taxonomy expansion (CLAUDE.md "Specialty Expansion"): from the original 5 categories
-- to 19, each backed by a NUCC taxonomy code individually verified against official/authoritative
-- sources (CMS crosswalk, NUCC-sourced lookups) this phase -- see docs/specialty-taxonomy-map.md
-- for the full source-by-source record. The search path itself needs no code change: it already
-- resolves specialty -> taxonomy codes entirely through specialty/specialty_taxonomy_mapping data
-- (ProviderSearchService), not a hardcoded list.

ALTER TABLE specialty ADD COLUMN description VARCHAR(300);

UPDATE specialty SET description = 'Providers listed under family medicine, general practice, and internal medicine NPI taxonomy classifications.' WHERE code = 'PRIMARY_CARE';
UPDATE specialty SET description = 'Providers listed under cardiovascular disease and interventional cardiology NPI taxonomy classifications.' WHERE code = 'CARDIOLOGY';
UPDATE specialty SET description = 'Providers listed under the dermatology NPI taxonomy classification.' WHERE code = 'DERMATOLOGY';
UPDATE specialty SET description = 'Providers listed under orthopaedic surgery and sports medicine NPI taxonomy classifications.' WHERE code = 'ORTHOPEDICS';
UPDATE specialty SET description = 'Providers listed under psychiatry, clinical psychology, clinical social work, mental health counseling, and marriage & family therapy NPI taxonomy classifications.' WHERE code = 'PSYCHIATRY_MENTAL_HEALTH';

INSERT INTO specialty (code, name, description) VALUES
    ('PEDIATRICS',                 'Pediatrics',                            'Providers listed under the pediatrics NPI taxonomy classification.'),
    ('OBSTETRICS_GYNECOLOGY',      'Obstetrics & Gynecology',               'Providers listed under the obstetrics & gynecology NPI taxonomy classification.'),
    ('NEUROLOGY',                  'Neurology',                             'Providers listed under the neurology NPI taxonomy classification (nervous system).'),
    ('GASTROENTEROLOGY',           'Gastroenterology',                      'Providers listed under the internal medicine gastroenterology NPI taxonomy classification (digestive system).'),
    ('ENDOCRINOLOGY',              'Endocrinology',                         'Providers listed under the internal medicine endocrinology, diabetes & metabolism NPI taxonomy classification.'),
    ('PULMONOLOGY',                'Pulmonology',                           'Providers listed under the internal medicine pulmonary disease NPI taxonomy classification (lungs and airways).'),
    ('NEPHROLOGY',                 'Nephrology',                            'Providers listed under the internal medicine nephrology NPI taxonomy classification (kidneys).'),
    ('UROLOGY',                    'Urology',                               'Providers listed under the urology NPI taxonomy classification.'),
    ('OPHTHALMOLOGY',              'Ophthalmology',                         'Providers listed under the ophthalmology NPI taxonomy classification (eyes).'),
    ('OTOLARYNGOLOGY',             'Otolaryngology / ENT',                  'Providers listed under the otolaryngology NPI taxonomy classification (ear, nose & throat).'),
    ('ALLERGY_IMMUNOLOGY',         'Allergy & Immunology',                  'Providers listed under the allergy & immunology NPI taxonomy classification.'),
    ('RHEUMATOLOGY',               'Rheumatology',                          'Providers listed under the internal medicine rheumatology NPI taxonomy classification (joints, muscles & autoimmune conditions).'),
    ('GENERAL_SURGERY',            'General Surgery',                       'Providers listed under the general surgery NPI taxonomy classification.'),
    ('PHYSICAL_MEDICINE_REHAB',    'Physical Medicine & Rehabilitation',    'Providers listed under the physical medicine & rehabilitation NPI taxonomy classification.');

INSERT INTO npi_taxonomy (taxonomy_code, classification, specialization, display_name) VALUES
    ('208000000X', 'Pediatrics',                     NULL,                                    'Pediatrician'),
    ('207V00000X', 'Obstetrics & Gynecology',        NULL,                                    'Obstetrician & Gynecologist'),
    ('2084N0400X', 'Psychiatry & Neurology',         'Neurology',                             'Neurologist'),
    ('207RG0100X', 'Internal Medicine',              'Gastroenterology',                      'Gastroenterologist'),
    ('207RE0101X', 'Internal Medicine',              'Endocrinology, Diabetes & Metabolism',  'Endocrinologist'),
    ('207RP1001X', 'Internal Medicine',              'Pulmonary Disease',                     'Pulmonologist'),
    ('207RN0300X', 'Internal Medicine',              'Nephrology',                            'Nephrologist'),
    ('208800000X', 'Urology',                        NULL,                                    'Urologist'),
    ('207W00000X', 'Ophthalmology',                  NULL,                                    'Ophthalmologist'),
    ('207Y00000X', 'Otolaryngology',                 NULL,                                    'Otolaryngologist (ENT)'),
    ('207K00000X', 'Allergy & Immunology',           NULL,                                    'Allergist & Immunologist'),
    ('207RR0500X', 'Internal Medicine',              'Rheumatology',                          'Rheumatologist'),
    ('208600000X', 'Surgery',                        NULL,                                    'General Surgeon'),
    ('208100000X', 'Physical Medicine & Rehabilitation', NULL,                                'Physiatrist');

INSERT INTO specialty_taxonomy_mapping (specialty_id, taxonomy_code)
SELECT s.id, m.taxonomy_code
FROM (VALUES
    ('PEDIATRICS',              '208000000X'),
    ('OBSTETRICS_GYNECOLOGY',   '207V00000X'),
    ('NEUROLOGY',               '2084N0400X'),
    ('GASTROENTEROLOGY',        '207RG0100X'),
    ('ENDOCRINOLOGY',           '207RE0101X'),
    ('PULMONOLOGY',             '207RP1001X'),
    ('NEPHROLOGY',              '207RN0300X'),
    ('UROLOGY',                 '208800000X'),
    ('OPHTHALMOLOGY',           '207W00000X'),
    ('OTOLARYNGOLOGY',          '207Y00000X'),
    ('ALLERGY_IMMUNOLOGY',      '207K00000X'),
    ('RHEUMATOLOGY',            '207RR0500X'),
    ('GENERAL_SURGERY',         '208600000X'),
    ('PHYSICAL_MEDICINE_REHAB', '208100000X')
) AS m (specialty_code, taxonomy_code)
JOIN specialty s ON s.code = m.specialty_code;

ALTER TABLE specialty ALTER COLUMN description SET NOT NULL;
