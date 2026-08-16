# End-to-end testing (Playwright)

Minimal, high-value coverage (CLAUDE.md 55-57) — not exhaustive. Runs against a real local
backend + Postgres + frontend dev server. Never depends on or calls any external payer API; the
one test that depends on live-imported NPPES data (`multi-location.spec.ts`) skips itself
cleanly if that data isn't present, rather than failing.

## Setup

```
cp .env.example .env
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run                       # terminal 1
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=import   # one-time, real NPPES data
cd frontend && npm run dev                                  # terminal 2
```

## Install browsers (one time)

```
cd frontend
npx playwright install chromium
```

## Run

```
cd frontend
npm run e2e
```

`playwright.config.ts` targets `http://localhost:5173` by default (override with
`E2E_BASE_URL`).

## What's covered

- Homepage loads with the search form.
- Cardiology + 90802 returns at least one real result.
- Selecting an unintegrated insurer shows the "not currently available" message and never blocks
  search.
- Provider detail: view, Directions link points at Google Maps, Back to results.
- Comparison: selecting two results and viewing the factual comparison table.
- Register → save a provider → sign out → sign back in → the saved provider is still there
  (proves the save persisted server-side, not just in local state).
- A provider with genuinely multiple real NPPES practice locations shows one selected office on
  its card/search result and an "Other locations" section on its detail page, each with its own
  working Directions link.

## Verified run (this phase)

Run locally against the real backend/Postgres/frontend with the live NPPES import already
applied (502 providers, including the real 41-location "SAMEDAY DOCTORS, P.C." organization —
see `docs/provider-ingestion.md`):

```
Running 7 tests using 1 worker
  7 passed (17.4s)
```

All 7 passed, including the multi-location test against the real "SAMEDAY DOCTORS, P.C." record
(41 genuine practice locations) — it showed one selected office plus an "Other locations" section
with a working, location-specific Directions link, exactly as designed.

Two real bugs in the test suite itself were caught and fixed by actually running it live (not
just writing it): `getByLabel('Specialty')` in non-exact mode ambiguously matched both the actual
specialty `<select>` and an unrelated "Find care by specialty" homepage section's accessible name;
and `locator.isVisible({ timeout })` does not retry/wait the way `expect(...).toBeVisible()` does,
so the multi-location test's soft-skip check was evaluating before the debounced name search had
returned any results, causing an unconditional skip.

## What's not automated (yet)

- CI wiring: not added this phase. Playwright needs a running Postgres + backend + frontend triad,
  which is more environment setup than this repo's current GitHub Actions jobs provide (see
  `.github/workflows/ci.yml`) — adding it well means provisioning those three services in CI, not
  just running `playwright test`. Documented here as the next step rather than rushed in
  half-configured (CLAUDE.md 57: "add CI only after stable local runs").
- The synthetic-demo-data-labeling flow (`docfitai.insurance.synthetic-demo.enabled=true`) isn't
  automated: it requires restarting the backend with a different environment variable mid-suite,
  which the other tests intentionally don't need. Manually verified instead — see the final
  report's manual test matrix.
