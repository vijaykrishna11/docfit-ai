# DocFit AI Web Beta v0.1.0-beta.1 -- Release Notes

Release candidate: `release/web-beta-v1`. Not yet deployed. Companion docs:
`docs/production-deployment-plan.md` (deployment architecture and requirements),
`docs/web-deployment-checklist.md` (exact first-deployment steps), `docs/data-coverage.md` (data
detail).

## What DocFit AI is

A healthcare navigation platform that helps users find and compare healthcare providers by
specialty, location, and (where evidence exists) insurance network participation. It does not
diagnose, interpret symptoms, recommend treatment, or prescribe medication -- strictly a provider
directory/discovery tool.

## Implemented product capabilities

- **Provider search**: by specialty (19 categories, real NUCC taxonomy mappings), ZIP code, city
  name, or browser geolocation; adjustable radius; nearest-first or name sort; pagination;
  shareable/bookmarkable search URLs.
- **Provider discovery map** with practical-fit filters (accessibility, hours-adjacent filters
  where sourced), list/map toggle on mobile (never forced split-screen).
- **Provider detail pages**: real practice locations (including genuine multi-location
  organizations, e.g. a real 41-office organization from live NPPES data), Call/Directions links,
  network evidence display where available.
- **Comparison**: select and compare multiple providers side by side on factual attributes only.
- **Accounts**: register/login/logout, JWT access tokens + HttpOnly refresh cookie, password
  hashing (bcrypt), rate-limited auth endpoints, account deletion with full data export first.
- **Saved data**: saved providers, saved searches, recent searches (browser-local), shortlists
  (create/share/remove), directory-data correction reports (anonymous or authenticated).
- **Care Navigator**: per-provider navigation status, a verification checklist, reminders, a saved
  plan -- privacy-first (own-account data only, never visible to other users or the provider).
- **Insurance network evidence**: a real payer/plan/network/evidence domain model; one demo payer
  has synthetic evidence for local development ONLY (refused outright in production); no live payer
  integration is wired in by default (the FHIR Plan-Net connector architecture exists, unconfigured).
- **Location suggestions**: deduplicated, ranked autocomplete across real loaded geography (no
  external geocoder at search time).
- **Data transparency panel**: real, live-queried provider/location/specialty/geography counts on
  the homepage -- never a hardcoded or marketing number, and explicitly distinguishes reference
  geography loaded from where provider data was actually imported.

## Data coverage (as of this release candidate, real counts)

| Metric | Count |
|---|---|
| Providers | 5,854 (3,956 individual, 1,898 organization) |
| Practice locations | 8,095 |
| Specialty categories | 19 |
| Reference geography (`zip_geography` rows) | 295 real LA County ZIP Code Tabulation Areas |
| Cities represented in reference geography | 89 |
| Counties represented | 1 (Los Angeles) |
| ZIPs actually queried for provider data | 30 of the 295 loaded |
| Last provider import | see `docs/data-coverage.md` for the current exact timestamp |

**Honest framing, not marketing**: having reference geography loaded for 295 ZIPs does **not** mean
provider data exists for all of them. Only 30 ZIPs were directly queried against NPPES so far.
Coverage is real and meaningfully larger than the original 6-ZIP demo footprint, but it is **partial
LA County coverage**, not full LA County or California coverage. The in-product Data Sources panel
states this distinction directly to users.

## Known limitations (stated honestly, not hidden before beta)

- **Geographic coverage is partial.** Real provider data exists for a bounded 30-ZIP subset of Los
  Angeles County. Searches outside the loaded/queried geography will return an honest "no data"
  result or an "unknown location" error, never a silently-wrong result from far away.
- **No guaranteed insurance coverage.** Only one demo payer has any network evidence, and it is
  synthetic (development/demo use only, disabled in production). A provider shown for a search does
  not mean a specific insurance plan is confirmed to cover them -- the product never claims this.
- **No real-time appointment availability.** DocFit AI is a directory, not a booking system.
- **No clinical recommendations of any kind.** Never diagnoses, interprets symptoms, or recommends
  treatment -- this is a hard architectural and product boundary, not a current-phase limitation.
- **Provider directory data can change** and is only as fresh as the last import. An operator-
  triggered refresh mechanism exists (`docs/data-refresh-operations.md`) but nothing runs on a
  schedule by default.
- **Address precision varies.** Most locations are `ZIP_CENTROID` precision (a ZIP's centroid, not
  the exact street address) -- an address-level geocoding pipeline exists but has not yet been run
  against the real dataset. The map and location displays label precision honestly rather than
  implying more accuracy than exists.
- **No nationwide coverage.** Los Angeles County only; California statewide and beyond are
  deliberately deferred, later work.
- **No password-reset flow yet.** No "Forgot password" link is shown, rather than promising a flow
  that doesn't work.

## Privacy model

- Saved providers, saved searches, shortlists, Care Navigator data (status/checklist/reminders/
  saved plan) are private to the owning account -- never visible to other users, never visible to
  the provider being tracked.
- Recent searches are browser-local only (never sent to or stored on the server).
- Full account data export and account deletion (with export offered first) are both available and
  covered by E2E tests.
- No synthetic/demo insurance data is ever seeded in production (enforced by a fail-fast startup
  guard, not just a default).

## Test results (this release candidate)

- Backend: 207/207 tests passing (`./mvnw --batch-mode verify`).
- Frontend: typecheck clean, lint clean (0 errors, 5 pre-existing non-blocking warnings), 44/44
  unit tests passing, production build succeeds.
- E2E (Playwright): 25/25 scenarios passing, run twice against the release candidate with no
  flakiness observed.
- `npm audit`: 0 vulnerabilities.
- Docker image builds successfully and was verified to actually run (see
  `docs/production-deployment-plan.md` "Docker + production rehearsal") -- migrations apply
  cleanly against a genuinely empty database, production-config refusals work correctly, and a
  fully valid production config serves real traffic including registration/login with a correctly-
  configured refresh cookie.

## Deployment readiness

Full checklist and exact steps: `docs/web-deployment-checklist.md`. The single highest-priority
open item before choosing a hosting provider is the cookie/CORS topology decision documented in
`docs/production-deployment-plan.md` ("Critical: cookie/CORS deployment topology") -- deploying
frontend and backend on two unrelated default subdomains of a platform like Render's
`*.onrender.com` will silently break session persistence.
