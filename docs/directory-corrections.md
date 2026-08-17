# Directory-data correction reports

A "Report incorrect information" feature that lets anyone flag a provider directory record as
possibly wrong. Full endpoint reference: `API.md` ("Directory-data correction reports"). This
document covers the product/privacy/security decisions.

## What it is, and isn't

This is a **directory-data correction** feature, not a feedback, rating, or review system, and
absolutely not a health-intake form. Allowed categories (`ReportType` enum, backend-allowlisted --
an unknown value is rejected by JSON deserialization before any application code runs):

- Wrong address
- Wrong phone number
- Provider no longer at this location
- Provider name appears incorrect
- Specialty information appears incorrect
- Duplicate provider/location
- Insurance/network information appears incorrect
- Other directory-data issue

Deliberately **not** asked, ever: diagnosis, reason for visit, medical history, treatment,
medication, or anything else clinical. The optional "Add details" field is a plain, length-bounded
(1000 characters) free-text comment about the *directory record*, not the user's health.

## Reports are review signals only

Submitting a report **never** alters the provider or location record it's about. There is no code
path anywhere in the application that reads `provider_data_report` and writes to `provider` or
`provider_location` -- the report table exists purely for an operator to review later (directly
against the database; no admin UI was built this phase, per CLAUDE.md's explicit "Provider Data
Report Admin -- Deferred UI"). The confirmation message reflects this honestly: "Thanks. This
report helps us review directory information" -- never a promise that the record will definitely
be corrected.

## Why anonymous submission

Two choices were available: allow anonymous submission (rate-limited), or require an account.
Anonymous was chosen deliberately:

- **Lower friction for a "quick correction."** Requiring an account to report "this phone number
  is wrong" is a disproportionate barrier for a low-stakes, low-effort action -- it would likely
  suppress genuine corrections more than it prevents abuse.
- **Bounded abuse surface.** A report is never rendered as HTML anywhere, never auto-applied to
  provider data, and is reviewed by a human before anything happens. The worst case for spam is
  operator review-queue noise, not a security or data-integrity incident.
- **Still rate-limited.** `ReportRateLimiter` (in-memory, per-IP, default 5 submissions per 10
  minutes) bounds volume regardless of authentication state. Same documented single-instance
  limitation as the existing `AuthRateLimiter` -- a multi-instance deployment would need a shared
  store to stay effective, deliberately not added just for this (no Redis dependency introduced
  solely for report-rate-limiting).
- **If signed in, still attributed.** A signed-in user's report is tagged with their user id for
  context (e.g. to help an operator judge trustworthiness), but this is never required.

## Validation (CLAUDE.md "Report Security")

- Provider must exist (**404** otherwise).
- If a location id is supplied, it must actually belong to the stated provider (**400**
  otherwise) -- prevents a report claiming "wrong phone" while pointing at an unrelated provider's
  office.
- `reportType` is constrained to the enum (see above).
- Comment is length-bounded at the DTO level (`@Size(max = 1000)`) and the database column level
  (`VARCHAR(1000)`) -- bounded twice, not just trusted client-side.
- No HTML is ever rendered from report content; there is no UI that displays report text back to
  any user (reports are operator-only, reviewed directly against the database).

## Data model

`provider_data_report` (V12 migration): `provider_id` (required FK), `provider_location_id`
(optional FK), `user_id` (optional FK -- null for anonymous), `report_type`, `comment`, `status`
(`NEW`/`REVIEWED`/`RESOLVED`/`DISMISSED`, defaults to `NEW`), `created_at`. No endpoint reads this
table back through the API -- operator review is a direct database query, documented here rather
than built as a UI this phase (deferred, per CLAUDE.md, not skipped silently).
