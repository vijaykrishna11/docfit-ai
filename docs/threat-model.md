# Threat model

A lightweight STRIDE-style pass over DocFit AI as of `feature/release-candidate-hardening`, written
from the actual code (not a generic template). Companion to `docs/api-security-matrix.md` (endpoint
inventory) and `docs/privacy-data-flow.md` (what's collected and where it goes).

## Assets worth protecting

1. User credentials (password hashes, refresh tokens).
2. A user's saved providers/searches (identity: which specialties/locations/providers a specific
   person is interested in).
3. Integrity of provider directory data and network evidence (users make real healthcare-navigation
   decisions based on this; wrong or fabricated data is a real-world harm, not just a bug).
4. Availability of the search path (the core product).

Explicitly **not** an asset in scope: clinical/medical data. DocFit AI collects none (see
`privacy-data-flow.md`), so there is no PHI-equivalent threat surface to model here beyond ordinary
account PII.

## Spoofing

- **Credential stuffing / brute force on login**: mitigated by `AuthRateLimiter` (IP+email sliding
  window, `AUTH_RATE_LIMIT_MAX_ATTEMPTS`/`AUTH_RATE_LIMIT_WINDOW_MINUTES`). Single-instance only
  (documented limitation) -- a multi-instance deployment needs a shared store to keep this effective;
  tracked as deployment-scaling follow-up.
- **JWT forgery**: access tokens are HMAC-SHA256 signed (`JwtService`, `io.jsonwebtoken`); the
  parser requires `verifyWith(key)`, so there is no "alg:none" downgrade path. Forgery requires the
  signing secret, which `ProductionSafetyValidator` refuses to let start under a known-placeholder or
  under-256-bit value in the `prod` profile.
- **Refresh token theft/replay**: refresh tokens are single-use and rotate on every use
  (`AuthService.refresh` revokes the presented token before issuing a new one); stored hashed
  (SHA-256) so a database read alone doesn't yield a usable token. **Not implemented**: reuse-chain
  revocation (if a since-rotated token is presented again, e.g. because it was stolen and used after
  the legitimate client already rotated it, the current code just rejects it as expired/revoked --
  it does not treat that as a signal to revoke the rest of that session's token chain). Classified as
  SHOULD FIX DURING BETA in the release scorecard: real theft-detection value, but it requires
  deciding a UX for "we think your session was compromised, please sign in again," which is a
  product decision, not a pure engineering one.

## Tampering

- **IDOR on user-scoped data**: reviewed directly (`api-security-matrix.md` "IDOR posture"). Every
  saved-provider/saved-search mutation is scoped server-side by the JWT-derived `userId`, never a
  client-supplied one. Covered by `SavedSearchAuthorizationTest` and `SavedProviderServiceTest`.
  Care Navigator (navigation status, verification checklist, reminders, saved plan, data export)
  applies the same rule -- covered by `NavigatorAuthorizationTest`, which registers two users and
  confirms neither can read or modify the other's status, checklist, reminder, saved plan, or
  export contents.
- **SQL injection**: reviewed every hand-written query in the codebase. All use parameterized
  JDBC/JPQL (`?`/named params), including the one query built with string concatenation
  (`ProviderNetworkEvidenceRepository`, which concatenates a *static* JPQL fragment, not user input).
  The one user-influenced "sort" parameter (`ProviderSearchService`) is matched against a fixed
  allow-list of constants (`equalsIgnoreCase` against `SORT_NAME`/`SORT_NAME_DESC`), never
  interpolated into SQL.
- **Fabricated/tampered provider or insurance data**: DocFit AI never accepts provider or network
  data from a request -- all of it comes from NPPES import, the operator-triggered CSV importer
  (source directory is server-config, never request-supplied), or the FHIR Plan-Net connector (base
  URL is server-config only). There is no authenticated *or* unauthenticated endpoint that writes to
  `provider`/`provider_location`/`provider_network_evidence`.

## Repudiation

- Out of scope at current scale: DocFit AI has no admin actions, financial transactions, or
  multi-party disputes that would need non-repudiation guarantees. Refresh token rotation events are
  implicitly auditable via `refresh_token.revoked_at`/`created_at`, which is sufficient for the
  current threat surface.

## Information disclosure

- **Actuator**: only `/actuator/health` is exposed (`SecurityConfig`, narrowed from a prior
  `/actuator/**` permitAll during this phase), with `show-details=never` and
  `management.endpoints.web.exposure.include=health` explicit in config (defense in depth against a
  future accidental widening).
- **Error responses**: `spring.web.error.include-message=always` surfaces hand-written,
  user-safe `ResponseStatusException` reasons -- never stack traces, SQL, or Hibernate internals
  (Spring Boot's default error handling doesn't include those in the JSON body regardless; verified
  no custom exception handler overrides that).
- **CORS**: explicit origin allow-list (`docfitai.cors.allowed-origins`), `allowCredentials(true)`,
  no wildcard, no origin-reflection. `ProductionSafetyValidator` refuses to start in `prod` if the
  list still references localhost/127.0.0.1.
- **Password/token logging**: grepped application code; none found.
- **Cross-user data leakage**: see IDOR above; also see the dedicated multi-location network-evidence
  isolation test (`NetworkEvidenceServiceTest.evidenceBoundToOneLocationNeverLeaksToTheProvidersOtherLocation`)
  -- evidence recorded for one of a provider's offices must never be shown as if it applied to a
  different office of the same provider.
- **XSS**: grepped for `dangerouslySetInnerHTML`/`innerHTML`/`document.write`/`eval` across
  `frontend/src`; none found. All rendered content goes through React's default JSX escaping.

## Denial of service

- **Unbounded search query** (fixed this phase): `ProviderSearchService`'s specialty match query
  had no geographic bound at all -- it loaded every location row nationwide for a specialty's
  taxonomy codes before filtering by distance in application code. At real NPPES scale this would be
  a severe cost amplification for common specialties. Fixed with a bounding-box SQL pre-filter plus
  a supporting index (V10 migration); radius and page size are now also upper-bounded
  (`MAX_RADIUS_MILES`, `MAX_PAGE_SIZE`) rather than accepting an arbitrarily large client-supplied
  value.
- **Auth endpoints**: rate-limited (see Spoofing above).
- **No file upload endpoints exist** (CSV import is operator-triggered from a server-configured
  directory, not a request body), so there's no unbounded-upload vector.
- **Bounded load test**: see `docs/release-checklist.md` for the concurrency levels exercised and
  results.

## Elevation of privilege

- **No role/admin concept exists yet** -- every authenticated user has identical privileges over
  only their own data. There is no privilege boundary to elevate across within the application
  itself.
- **CSRF**: deliberately disabled at the Spring Security level, with the rationale documented
  in-line in `SecurityConfig`'s class Javadoc -- the only cookie-authenticated endpoints
  (`/api/auth/refresh`, `/api/auth/logout`) use a `SameSite=Lax`, path-scoped cookie (browsers
  withhold `Lax` cookies on cross-site POSTs, which is the exact vector CSRF tokens exist to close),
  and every other authenticated endpoint requires a bearer token a cross-site page cannot read.

## SSRF

No endpoint accepts a URL, hostname, or path from a request for the backend to fetch. Both outbound
HTTP clients (`NppesClient`, `FhirPlanNetConnector`) use a fixed or server-config-only base URL; the
CSV importer's source directory is likewise server-config only, never request-supplied.

## Configuration leak

`ProductionSafetyValidator` (`backend/src/main/java/com/docfitai/backend/config/`) is a fail-fast
guard, not a runtime mitigation -- it refuses to *start* the `prod` profile at all if the JWT secret
is missing/placeholder/short, the refresh cookie isn't `Secure`, CORS still points at localhost, or
synthetic-insurance/CSV-import flags are left on. Originally wired as a `@Profile("prod") @Bean`,
which a live boot test showed could run *after* `DataSource`/Flyway had already attempted a real
database connection depending on bean-creation order; moved to a
`ProductionSafetyEnvironmentListener` on `ApplicationEnvironmentPreparedEvent`, which runs before the
`ApplicationContext` exists at all, so no bean -- including one that touches the network or database
-- can possibly run first. Verified live (see commit `1548237`): a missing secret, a weak secret, and an insecure cookie flag
each fail with the guard's own message before any DB connection attempt.
