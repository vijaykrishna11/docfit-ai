# Privacy and data flow

What DocFit AI collects, why, where it lives, and how it leaves. Written as a standalone reference
for the whole application; `docs/insurance-network-architecture.md` ("Privacy", "Data retention")
covers the insurance-evidence-specific subset in more depth and is not duplicated here.

## Core principle

DocFit AI's account/saved-data model only ever stores what a signed-in user explicitly asked it to
store. Nothing is inferred, auto-recorded, or persisted server-side from browsing behavior. Browsing
without an account works fully and stores nothing server-side at all.

## What's collected, and where it lives

| Data | Collected when | Stored | Retention |
|---|---|---|---|
| Email, password (BCrypt hash), display name | Registration | `app_user` table | Until account deletion |
| Refresh tokens (SHA-256 hash of the raw token, never the raw token itself) | Login/refresh | `refresh_token` table | Until expiry (`REFRESH_TOKEN_TTL_DAYS`, default 30d) or explicit revocation (logout, rotation, account deletion) |
| Saved providers | User clicks "Save" on a provider | `saved_provider` table | Until unsaved or account deletion |
| Saved searches | User clicks "Save this search" | `saved_search` table (specialty, location text, lat/lng, radius, sort -- exactly the search criteria, nothing else) | Until deleted or account deletion |
| Recently-viewed providers | Viewing a provider detail page | Browser `sessionStorage` only (`frontend/src/utils/recentlyViewed.ts`) -- never sent to the server, never tied to an account | Cleared when the browser tab/session ends |
| Compare list | Adding providers to the compare view | Browser `sessionStorage` only (`frontend/src/context/CompareContext.tsx`) | Cleared when the browser tab/session ends |
| Selected insurance plan (`planId`) | Selecting a plan in search | URL query string only -- never persisted server-side (see `insurance-network-architecture.md` "Privacy") | Gone when the URL changes; not stored |

Nothing else is collected. In particular: no health/medical data, no symptoms, no diagnoses, no
insurance member/group ID, no payment information, no device fingerprinting, no third-party
analytics or tracking SDKs (verified by grep -- there is no Google Analytics, Segment, Mixpanel,
Hotjar, or equivalent integration anywhere in `frontend/`).

## Access token handling (frontend)

The JWT access token lives in a module-level JS variable in `frontend/src/api/client.ts` only --
never `localStorage`, never `sessionStorage`, never a cookie. It exists only in memory for the
lifetime of the tab and is gone on reload (silently re-obtained via the refresh cookie on the next
page load if the session is still valid). This means an XSS payload that could read
`localStorage`/cookies would still not find a usable access token sitting there at rest -- though a
live XSS payload could still call authenticated APIs directly using the page's own JS context and
in-memory token, which is why the separate XSS review (grepped for `dangerouslySetInnerHTML`/
`innerHTML`/`eval` -- none found in `frontend/src`) matters as the actual first line of defense.

The refresh token is different: it must survive a page reload, so it lives in an httpOnly (JS
cannot read it), `SameSite=Lax`, path-scoped (`/api/auth` only) cookie, `Secure` in any real
deployment (`docfitai.auth.cookie-secure`, enforced by `ProductionSafetyValidator` under the `prod`
profile). httpOnly means an XSS payload cannot read or exfiltrate it either.

## What third parties ever see

- **NPPES NPI Registry** (`NppesClient`): outbound only, one-directional. DocFit AI sends a postal
  code and specialty/entity-type filter to look up provider data; it never sends any user-identifying
  information (no email, no account id, no search-user's location beyond the ZIP being queried) --
  the NPPES API is queried for its own already-public directory data, not to identify a DocFit user.
- **FHIR Plan-Net connector** (`FhirPlanNetConnector`): no live payer endpoint is wired in by default
  (`docfitai.insurance.fhir-plan-net.base-url` is empty unless an operator explicitly sets it). If
  ever configured, the same one-directional shape applies: DocFit AI queries a payer's public
  directory data by NPI, never sends user-identifying information to the payer.
- **No other outbound calls exist.** Grepped for `RestClient`/`RestTemplate`/`HttpClient`/`WebClient`
  usage across the backend; only the two clients above make outbound HTTP calls.

## Logging

Grepped application code for logging of passwords or tokens: none found. Application logs
(`logging.level.*`) record request-level and service-level events (search executed, import
progress, auth rate-limit hits) -- never raw request bodies, passwords, JWTs, or refresh tokens.
Connector logs (NPPES, FHIR Plan-Net) record source/status/duration/counts only, never the raw
response payload (see `insurance-network-architecture.md` "Data retention").

## Account deletion

`DELETE /api/auth/me` (`AuthService.deleteAccount`) removes the `app_user` row and cascades to that
user's own `refresh_token`, `saved_provider`, and `saved_search` rows only -- verified by reading
`AuthService.deleteAccount` directly (no cross-user data is touched; foreign keys are scoped to
`user_id`). Provider directory data and network evidence are not user data and are unaffected by any
account deletion, by design -- they describe providers, not the requesting user.

## Known gap

`AuthController.clientIp()` records the raw client IP address (via `HttpServletRequest.getRemoteAddr()`)
transiently, in memory only, as the rate limiter's bucketing key -- it is never persisted to a table
or log line beyond the limiter's own in-memory sliding window, which expires per
`AUTH_RATE_LIMIT_WINDOW_MINUTES` (default 5 minutes). Not itself a privacy issue given it's never
stored, but see `docs/api-security-matrix.md` ("Known, deliberate, documented gap") for why this
resolves to the wrong IP behind a reverse proxy without additional deployment-layer configuration.
