# API security matrix

Full inventory of every REST endpoint exposed by the backend (`backend/src/main/java/.../*Controller.java`),
as of `feature/release-candidate-hardening`. Access levels are enforced in
`backend/src/main/java/com/docfitai/backend/auth/SecurityConfig.java`; this document mirrors that
configuration and adds purpose/sensitivity/rate-limit context. If a route here ever disagrees with
`SecurityConfig`, `SecurityConfig` is the source of truth -- update this file to match it, not the
other way around.

Legend: **Public** = no authentication required. **Auth** = requires a valid bearer access token
(`Authentication` principal is `AuthenticatedUser`; anonymous requests get 401). **User-scoped** =
Auth, and every row returned/mutated is filtered by the caller's own `userId` server-side (never a
client-supplied id) -- see the IDOR notes below the table.

| Method | Path | Access | Purpose | Sensitive data | Rate limit |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | Public | Create an account | Email, password (hashed at rest, BCrypt) | Yes -- `AuthRateLimiter`, keyed by IP+email |
| POST | `/api/auth/login` | Public | Authenticate | Email, password | Yes -- same limiter |
| POST | `/api/auth/refresh` | Public (requires the httpOnly `docfit_refresh` cookie) | Rotate an access token | Refresh token (cookie only, never in JS) | No explicit limiter; single-use/rotating token bounds abuse |
| POST | `/api/auth/logout` | Public (requires the refresh cookie; no-ops without one) | End a session | Refresh token | No |
| GET | `/api/auth/me` | Auth | Read own profile | Email, display name | No |
| PATCH | `/api/auth/me` | Auth | Update own display name | Display name | No |
| DELETE | `/api/auth/me` | Auth | Delete own account (cascades to saved data/refresh tokens for that user only) | -- | No |
| GET | `/api/specialties` | Public | Reference data (care categories) | None | No |
| GET | `/api/insurance-carriers` | Public | Legacy informational carrier list (`@deprecated`, superseded by `/api/insurance/payers`) | None | No |
| GET | `/api/insurance/payers` | Public | Insurance payer list | None | No |
| GET | `/api/insurance/payers/{id}/plans` | Public | Plans for a payer | None | No |
| GET | `/api/locations/suggestions` | Public | ZIP/city autocomplete | None (query text is a location string, not PII by itself) | No |
| GET | `/api/providers/by-name` | Public | Provider name search | Provider directory data (already public NPPES data) | No |
| GET | `/api/providers/search` | Public | Specialty+location provider search | Same | No |
| GET | `/api/providers/{id}` | Public | Provider detail | Same | No |
| GET | `/api/providers/{id}/network-evidence` | Public | Network directory evidence for one provider+plan(+location) | Same; never a coverage guarantee (see `docs/insurance-network-architecture.md`) | No |
| GET | `/api/saved-providers` | User-scoped | List the caller's saved providers | Which providers a specific user saved | No |
| POST | `/api/saved-providers/{providerId}` | User-scoped | Save a provider | Same | No |
| DELETE | `/api/saved-providers/{providerId}` | User-scoped | Unsave a provider | Same | No |
| GET | `/api/saved-searches` | User-scoped | List the caller's saved searches | Which searches (specialty/location) a specific user saved | No |
| POST | `/api/saved-searches` | User-scoped | Save a search | Same | No |
| PATCH | `/api/saved-searches/{id}` | User-scoped | Rename a saved search | Same | No |
| DELETE | `/api/saved-searches/{id}` | User-scoped | Delete a saved search | Same | No |
| GET | `/actuator/health` | Public | Liveness probe | None (`show-details=never`) | No |

## Why the provider/insurance/reference GET routes are public

CLAUDE.md's mission is unauthenticated healthcare navigation -- requiring sign-in to search would
contradict the product's purpose. None of these routes expose anything beyond already-public NPPES
provider directory data and payer-published network directory data. Nothing here is user-specific.

## IDOR posture (user-scoped routes)

Every user-scoped route resolves the acting user from the validated JWT's principal
(`AuthenticatedUser.userId()`), never from a path/query parameter -- there is no `userId` request
parameter anywhere in these controllers for a client to tamper with. Ownership is enforced at the
repository/query level, not just checked after the fact:

- `SavedProviderService.save/remove` key by `(userId, providerId)` directly; there is no
  saved-provider row id exposed to the client at all for `remove` to target.
- `SavedSearchService.rename/remove` use `findByIdAndUserId(id, userId)` -- a saved search owned by
  another user simply doesn't exist from the caller's perspective (404, not 403, so existence isn't
  leaked either).

Regression coverage: `SavedSearchAuthorizationTest.userCannotReadOrDeleteAnotherUsersSavedSearch`,
`SavedProviderServiceTest` (ownership scoping + concurrency).

## Rate limiting

Only `/api/auth/register` and `/api/auth/login` are rate-limited (`AuthRateLimiter`, in-memory,
sliding window, keyed by client IP + attempted email). This is a deliberate, narrow scope: these are
the only endpoints where unlimited attempts directly enable credential-stuffing/enumeration. The
limiter is single-instance (documented in `AuthRateLimiter`'s Javadoc) -- horizontal scaling would
need a shared store (e.g. Redis) to stay effective; noted as a deployment-scaling follow-up, not a
correctness bug at current (single-instance) deployment scale.

Every other route is either read-only public reference/directory data (no credential-stuffing
surface) or already bounded by authentication plus per-user data volume (a signed-in user can only
save/list their own handful of providers/searches).

## Known, deliberate, documented gap

`AuthController.clientIp()` uses `HttpServletRequest.getRemoteAddr()` directly, not an
`X-Forwarded-For`-aware resolution. Behind a reverse proxy/load balancer, every request would
appear to originate from the proxy's IP, degrading the rate limiter's IP-based bucketing (not
defeating it -- the email half of the IP+email key still applies) to email-only. This is
intentionally *not* patched by trusting `X-Forwarded-For` blindly, since that header is
attacker-controlled unless the specific reverse proxy is configured to strip/overwrite it -- doing
that safely requires knowing the actual deployment topology. Tracked in
`docs/production-deployment-plan.md` as a deployment-time configuration step (set a trusted-proxy
count/allowlist matching the real infrastructure), not fixed speculatively here.
