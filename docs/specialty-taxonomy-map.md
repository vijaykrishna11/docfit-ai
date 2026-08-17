# Specialty → taxonomy map

DocFit AI's consumer-facing specialty categories, and the exact NUCC Health Care Provider
Taxonomy code(s) each one is backed by. Companion docs: `docs/provider-source-research.md`
(source decisions), `API.md` (`GET /api/specialties`).

## Why a category is not one taxonomy

A consumer category can legitimately map to more than one NUCC code -- e.g. "Cardiology" covers
both the general Cardiovascular Disease specialization and Interventional Cardiology. DocFit AI
never pretends a category equals exactly one taxonomy; `specialty_taxonomy_mapping` is a proper
many-to-many table, and every search result also carries the *actual matched taxonomy's* display
name (`WhyThisResult` renders "Matches Cardiovascular Disease Specialist," not "Matches
Cardiology") so nothing is hidden behind the category label.

## Source verification

Every code below was checked against at least one authoritative or cross-corroborating source
during this phase (NUCC's own published taxonomy PDFs, the CMS Medicare taxonomy crosswalk, and
independent taxonomy-lookup references that mirror the same NUCC data) -- not a single blog post,
and never a scraped provider-directory site. The original 5 categories (Primary Care, Cardiology,
Dermatology, Orthopedics, Psychiatry/Mental Health) were verified in an earlier phase and are
included here for completeness, unchanged.

| Category | Code | Description |
|---|---|---|
| Primary Care | `PRIMARY_CARE` | Family Medicine, General Practice, Internal Medicine |
| Cardiology | `CARDIOLOGY` | Cardiovascular Disease, Interventional Cardiology |
| Dermatology | `DERMATOLOGY` | Dermatology |
| Orthopedics | `ORTHOPEDICS` | Orthopaedic Surgery, Sports Medicine |
| Psychiatry / Mental Health | `PSYCHIATRY_MENTAL_HEALTH` | Psychiatry, Clinical Psychology, Clinical Social Work, Mental Health Counseling, Marriage & Family Therapy |
| Pediatrics | `PEDIATRICS` | Pediatrics |
| Obstetrics & Gynecology | `OBSTETRICS_GYNECOLOGY` | Obstetrics & Gynecology |
| Neurology | `NEUROLOGY` | Neurology |
| Gastroenterology | `GASTROENTEROLOGY` | Internal Medicine → Gastroenterology |
| Endocrinology | `ENDOCRINOLOGY` | Internal Medicine → Endocrinology, Diabetes & Metabolism |
| Pulmonology | `PULMONOLOGY` | Internal Medicine → Pulmonary Disease |
| Nephrology | `NEPHROLOGY` | Internal Medicine → Nephrology |
| Urology | `UROLOGY` | Urology |
| Ophthalmology | `OPHTHALMOLOGY` | Ophthalmology |
| Otolaryngology / ENT | `OTOLARYNGOLOGY` | Otolaryngology |
| Allergy & Immunology | `ALLERGY_IMMUNOLOGY` | Allergy & Immunology |
| Rheumatology | `RHEUMATOLOGY` | Internal Medicine → Rheumatology |
| General Surgery | `GENERAL_SURGERY` | Surgery |
| Physical Medicine & Rehabilitation | `PHYSICAL_MEDICINE_REHAB` | Physical Medicine & Rehabilitation |

## Exact codes (this phase's additions)

| Taxonomy code | Classification | Specialization | Consumer category |
|---|---|---|---|
| `208000000X` | Pediatrics | -- | Pediatrics |
| `207V00000X` | Obstetrics & Gynecology | -- | Obstetrics & Gynecology |
| `2084N0400X` | Psychiatry & Neurology | Neurology | Neurology |
| `207RG0100X` | Internal Medicine | Gastroenterology | Gastroenterology |
| `207RE0101X` | Internal Medicine | Endocrinology, Diabetes & Metabolism | Endocrinology |
| `207RP1001X` | Internal Medicine | Pulmonary Disease | Pulmonology |
| `207RN0300X` | Internal Medicine | Nephrology | Nephrology |
| `208800000X` | Urology | -- | Urology |
| `207W00000X` | Ophthalmology | -- | Ophthalmology |
| `207Y00000X` | Otolaryngology | -- | Otolaryngology / ENT |
| `207K00000X` | Allergy & Immunology | -- | Allergy & Immunology |
| `207RR0500X` | Internal Medicine | Rheumatology | Rheumatology |
| `208600000X` | Surgery | -- | General Surgery |
| `208100000X` | Physical Medicine & Rehabilitation | -- | Physical Medicine & Rehabilitation |

## Deliberately excluded

- **Sub-specialization codes** (e.g. Pediatric Endocrinology `2080P0205X`, Ophthalmology-Retina
  `207WX0107X`, Vascular Surgery `2086S0129X`) -- classification-level codes only, matching the
  existing pattern from the original 5 categories ("stable across recent NUCC versions," per the
  original V2 migration's own comment). A future phase could add these as finer-grained filters
  within a category if a real product need shows up; nothing here blocks that.
- **Neurological Surgery, Cardiac Surgery, and other surgical sub-specialties as separate
  categories** -- General Surgery (`208600000X`) covers the general-surgeon classification only;
  the surgical sub-specialties each have their own distinct NUCC classification codes and were
  judged not worth a dedicated consumer category yet at this data-coverage stage (no imported
  providers to serve them meaningfully today -- see `docs/data-coverage.md`).
- **Any taxonomy-code guess not independently corroborated.** Every code in the tables above was
  cross-checked against more than one source before being seeded; none were seeded from a single
  unverified reference.

## Grouping (frontend presentation only)

The search form's specialty `<select>` groups these 19 categories into five `<optgroup>` labels
(Primary care / Medical specialties / Surgical specialties / Behavioral health / Other) purely for
scannability -- this is a client-side presentation choice (`SearchForm.tsx`), not a second copy of
the specialty list itself (which always comes from `GET /api/specialties`). A specialty code with
no explicit group mapping falls into "Other" rather than being silently hidden, so a new backend
specialty is never invisible just because the frontend grouping map hasn't been updated yet.

## Data coverage vs. architecture

Search itself needed **no code change** to support the new categories -- `ProviderSearchService`
already resolves specialty → taxonomy codes entirely from `specialty`/`specialty_taxonomy_mapping`
table data, and `NppesImportRunner` already reads its "known taxonomy codes" set from
`npi_taxonomy` at import time. Both were architected generically from day one. A real, bounded
NPPES import was re-run this phase against the existing 6 demo ZIPs specifically to verify this
claim against live data, not just reason about it: 90 new providers and 146 new locations were
created, and 13 of the 14 new categories returned real search results within 50 miles of Long
Beach, CA (Otolaryngology returned 0 -- an honest gap in the current small demo-ZIP dataset, not a
bug). See `docs/data-coverage.md` for the full, current numbers.
