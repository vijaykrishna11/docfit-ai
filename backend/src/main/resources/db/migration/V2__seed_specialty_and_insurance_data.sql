-- Taxonomy codes are from the NUCC Health Care Provider Taxonomy Code Set,
-- version 26.1 (https://taxonomy.nucc.org/). Each code below was individually
-- verified (classification + specialization) against NUCC-sourced lookups
-- before being seeded here; these classification-level codes have been
-- stable across recent NUCC versions.
--
-- Pediatrics (208000000X) is intentionally excluded from Primary Care per
-- TODAY-MVP scope. Primary Care is Family Medicine, General Practice, and
-- Internal Medicine only.

INSERT INTO specialty (code, name) VALUES
    ('PRIMARY_CARE',              'Primary Care'),
    ('CARDIOLOGY',                'Cardiology'),
    ('DERMATOLOGY',               'Dermatology'),
    ('ORTHOPEDICS',               'Orthopedics'),
    ('PSYCHIATRY_MENTAL_HEALTH',  'Psychiatry / Mental Health');

INSERT INTO npi_taxonomy (taxonomy_code, classification, specialization, display_name) VALUES
    ('207Q00000X', 'Family Medicine',              NULL,                        'Family Medicine Physician'),
    ('208D00000X', 'General Practice',              NULL,                        'General Practice Physician'),
    ('207R00000X', 'Internal Medicine',             NULL,                        'Internal Medicine Physician'),
    ('207RC0000X', 'Internal Medicine',             'Cardiovascular Disease',    'Cardiovascular Disease Specialist'),
    ('207RI0011X', 'Internal Medicine',             'Interventional Cardiology', 'Interventional Cardiology Specialist'),
    ('207N00000X', 'Dermatology',                   NULL,                        'Dermatologist'),
    ('207X00000X', 'Orthopaedic Surgery',           NULL,                        'Orthopaedic Surgeon'),
    ('207XX0005X', 'Orthopaedic Surgery',           'Sports Medicine',           'Sports Medicine (Orthopaedic Surgery) Physician'),
    ('2084P0800X', 'Psychiatry & Neurology',        'Psychiatry',                'Psychiatrist'),
    ('103TC0700X', 'Psychologist',                  'Clinical',                  'Clinical Psychologist'),
    ('1041C0700X', 'Social Worker',                 'Clinical',                  'Clinical Social Worker'),
    ('101YM0800X', 'Counselor',                     'Mental Health',             'Mental Health Counselor'),
    ('106H00000X', 'Marriage & Family Therapist',   NULL,                        'Marriage & Family Therapist');

INSERT INTO specialty_taxonomy_mapping (specialty_id, taxonomy_code)
SELECT s.id, m.taxonomy_code
FROM (VALUES
    ('PRIMARY_CARE',             '207Q00000X'),
    ('PRIMARY_CARE',             '208D00000X'),
    ('PRIMARY_CARE',             '207R00000X'),
    ('CARDIOLOGY',               '207RC0000X'),
    ('CARDIOLOGY',               '207RI0011X'),
    ('DERMATOLOGY',              '207N00000X'),
    ('ORTHOPEDICS',              '207X00000X'),
    ('ORTHOPEDICS',              '207XX0005X'),
    ('PSYCHIATRY_MENTAL_HEALTH', '2084P0800X'),
    ('PSYCHIATRY_MENTAL_HEALTH', '103TC0700X'),
    ('PSYCHIATRY_MENTAL_HEALTH', '1041C0700X'),
    ('PSYCHIATRY_MENTAL_HEALTH', '101YM0800X'),
    ('PSYCHIATRY_MENTAL_HEALTH', '106H00000X')
) AS m (specialty_code, taxonomy_code)
JOIN specialty s ON s.code = m.specialty_code;

INSERT INTO insurance_carrier (name) VALUES
    ('Aetna'),
    ('Anthem Blue Cross'),
    ('Blue Shield of California'),
    ('Cigna'),
    ('Kaiser Permanente'),
    ('UnitedHealthcare'),
    ('Medicare'),
    ('Medi-Cal');
