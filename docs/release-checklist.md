# Release checklist

Evidence-backed status as of `feature/release-candidate-hardening`. "Verified" means directly
tested during this phase (command run, output inspected), not assumed from reading code. Companion
documents: `docs/threat-model.md`, `docs/privacy-data-flow.md`, `docs/api-security-matrix.md`,
`docs/operations-runbook.md`, `docs/production-deployment-plan.md`.

## Automated tests

| Suite | Result | Command |
|---|---|---|
| Backend (`./mvnw verify`) | 81/81 passing | `cd backend && ./mvnw -o --batch-mode verify` |
| Frontend unit/component (Vitest) | 36/36 passing (11 files) | `cd frontend && npm run test -- --run` |
| Frontend typecheck | Clean, no errors | `cd frontend && npm run typecheck` |
| Frontend lint | 0 errors, 3 pre-existing warnings (react-refresh export-components in context files -- cosmetic, not a bug) | `cd frontend && npm run lint` |
| Frontend production build | Succeeds; bundle ~316 kB JS (93.9 kB gzip), ~38.8 kB CSS (7.0 kB gzip) | `cd frontend && npm run build` |
| E2E (Playwright) | See "E2E status" below | `cd frontend && npx playwright test` |

## Security

- Full endpoint inventory + access-level audit: `docs/api-security-matrix.md`.
- IDOR: verified server-side ownership scoping on every user-scoped route (no client-supplied
  `userId` anywhere); `SavedSearchAuthorizationTest`, `SavedProviderServiceTest`.
- XSS: grepped `frontend/src` for `dangerouslySetInnerHTML`/`innerHTML`/`document.write`/`eval` --
  none found.
- SQL injection: every query parameterized; the one user-influenced value (`sort`) is matched
  against a fixed allow-list, never interpolated.
- SSRF: no endpoint accepts a URL/host from a request; both outbound HTTP clients use a fixed or
  server-config-only base URL.
- Token storage: access token is in-memory only (never `localStorage`/`sessionStorage`); refresh
  token is httpOnly/path-scoped/`SameSite=Lax`, `Secure` enforced in `prod`.
- Actuator: narrowed from `/actuator/**` to `/actuator/health` only this phase, plus explicit
  exposure config as defense in depth.
- Production config fail-fast: `ProductionSafetyValidator` verified live (not just unit-tested) --
  booting with `spring.profiles.active=prod` and a missing/weak JWT secret, an insecure cookie flag,
  or a localhost CORS origin each refuse to start with a clear message, before any database
  connection is attempted. See `docs/threat-model.md` ("Configuration leak") for the bean-ordering
  bug this caught and fixed along the way.
- Healthcare/insurance language audit: grepped frontend/backend/docs for unqualified coverage
  claims ("covered", "guaranteed", "in-network" without qualification, "$0 visit"); every match
  found was already correctly hedged, and `networkEvidenceDisplay.test.ts`/`ProviderResults.test.tsx`
  actively assert against ever rendering an unqualified claim.

## Correctness fixes made this phase (with regression tests)

1. **Unbounded nationwide provider search query** -- `ProviderSearchService` had no geographic
   bound at all; fixed with a bounding-box SQL pre-filter + index (V10), radius/page-size upper
   bounds. See `docs/geospatial-scaling.md` for measured before/after evidence (20,011 rows -> 14
   rows returned to the app for the same worst-case query). Test:
   `oversizedRadiusAndPageSizeAreClampedRatherThanHonoredVerbatim`.
2. **Concurrent 401 refresh race** -- two requests hitting a 401 at once each independently called
   the refresh handler, doubling refresh traffic. Fixed with an in-flight-promise single-flight
   guard in `frontend/src/api/client.ts`. Test: `client.test.ts` "shares one in-flight refresh...".
3. **Stale search response race** -- rapid search-param changes had no guard against an
   out-of-order response overwriting newer results. Fixed with the same closure-cancellation
   pattern already used elsewhere in `HomePage.tsx`.
4. **Saved-provider double-click race** -- concurrent save requests for the same provider could
   throw `UnexpectedRollbackException` (a real bug found by writing the concurrency test, not by
   inspection alone -- a first attempted fix using try/catch inside `@Transactional` still failed).
   Fixed with `INSERT ... ON CONFLICT DO NOTHING`. Test:
   `concurrentSaveOfTheSameProviderNeverThrowsAndLeavesExactlyOneRow` (real two-thread test against
   Postgres, not simulated).
5. **Network-evidence location isolation** -- already correctly implemented, but untested at the
   exact scenario it exists to prevent. Added
   `evidenceBoundToOneLocationNeverLeaksToTheProvidersOtherLocation`.

## Database / migrations

- V1-V10 apply cleanly to a fresh database (every backend integration test does this via
  Testcontainers on every run).
- **Real upgrade-in-place migration verified**: booted the packaged jar against a real, pre-existing
  Postgres database (492 real providers, previously at schema v9 from earlier development) and
  confirmed Flyway applied V10 in place with zero data loss -- `flyway migrated schema "public" to
  version "10"`, provider count unchanged afterward, application served real search/detail requests
  correctly post-migration.
- Constraint audit: `saved_provider` has `UNIQUE (user_id, provider_id)` (exercised directly by the
  concurrency fix above); `app_user.email` and `refresh_token.token_hash` are `UNIQUE`.

## Performance (measured, real data)

Against the real dataset described above (492 providers, 689 locations), local Postgres, backend
run outside a container (JIT-warmed, not cold-start):

| Endpoint | n | min | p50 | p95 | max | avg |
|---|---|---|---|---|---|---|
| `GET /api/providers/search` | 20 | 14ms | 16ms | 18ms | 21ms | 16ms |
| `GET /api/providers/{id}` | 20 | 13ms | 17ms | 20ms | 20ms | 17ms |
| `GET /api/providers/by-name` | 20 | 8ms | 10ms | 13ms | 13ms | 10ms |
| `GET /api/locations/suggestions` | 20 | 9ms | 11ms | 14ms | 19ms | 12ms |

Bounded concurrent load, `GET /api/providers/search` (same query, `xargs -P` parallel `curl`):

| Concurrency | n | min | p50 | p95 | max | avg |
|---|---|---|---|---|---|---|
| 25 | 25 | 18ms | 22ms | 28ms | 50ms | 24ms |
| 50 | 50 | 17ms | 20ms | 23ms | 55ms | 20ms |

All comfortably interactive at current data scale. See `docs/geospatial-scaling.md` for the
20,000-row synthetic scale simulation (run in a rolled-back transaction, never committed) and its
honest findings about where the new bounding-box index does and does not help.

## E2E status

Playwright suite exists (`frontend/e2e/`) from the prior phase covering core auth/search/save flows.
Not re-run to green in this session's remaining scope -- listed in "Remaining/deferred work" below
rather than claimed as verified.

## Remaining / deferred work

Honest accounting of what this phase's time did not reach, rather than claiming completion:

- Full E2E re-run and stabilization pass (flaky-test audit, `waitForTimeout` removal).
- Accessibility keyboard-only walkthrough and automated scan.
- Responsive breakpoint screenshot pass (375/390/768/1024/1440).
- Backup/restore rehearsal.
- Request correlation ID / structured request logging.
- Dependency audit (`npm audit`) with targeted fixes.
- Refresh-token reuse-chain revocation (see `docs/threat-model.md` "Spoofing" -- classified
  SHOULD FIX DURING BETA, not a blocker).

See the final phase report for the full blocker classification (MUST FIX BEFORE DEPLOYMENT / SHOULD
FIX DURING BETA / SAFE TO DEFER) and production readiness scorecard.
