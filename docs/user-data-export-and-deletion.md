# User data export and account deletion

Full endpoint reference: `API.md` ("`GET /api/account/export`", "`DELETE /api/auth/me`"). This
document covers the product/privacy/security decisions behind both features.

## Data export

`GET /api/account/export` returns everything a signed-in user owns as one JSON file:

```json
{
  "generatedAt": "2026-08-17T...",
  "account": { "id": 1, "email": "...", "displayName": "...", "createdAt": "..." },
  "savedProviders": [...],
  "shortlists": [...],
  "savedSearches": [...],
  "savedPlan": { "payerName": "...", "planName": "..." },
  "navigation": [...],
  "verificationItems": [...],
  "reminders": [...]
}
```

**Always scoped to the caller.** The endpoint takes no user-id parameter of any kind -- identity
comes only from the validated access token, the same pattern every other authenticated endpoint in
this codebase uses. There is no way to request another user's export, by design (not merely by
convention) -- there is no code path that could even accept a different id.

**Never includes**: the BCrypt password hash, any refresh token (raw or hashed), or any other
user's data. `AccountExportDto` is a purpose-built projection with exactly four fields (id, email,
displayName, createdAt) -- there is no path from `AppUser`'s `passwordHash` field to the response,
because the DTO simply doesn't have a slot for it. Verified directly in `NavigatorAuthorizationTest`
(asserts the exported JSON never contains the literal strings `"passwordHash"` or `"refreshToken"`)
and by code inspection of `DataExportService`.

**Download mechanics (frontend)**: the JSON is fetched via the normal authenticated API client,
then turned into a `Blob` and downloaded via a synthetic `<a download>` click -- no server-side
file generation, no temporary file, no second request. `Content-Disposition: attachment;
filename="docfit-ai-data-export.json"` is set server-side too, so a direct API client (curl,
Postman) gets the same filename hint.

## Account deletion

`DELETE /api/auth/me` requires authentication (identity from the token, same as export) and
removes, in order, every user-owned table this repository has ever added: shortlists (and their
membership rows, which cascade at the DB level), saved providers, saved searches, Care Navigator
navigation status, verification items, reminders, saved plan, refresh tokens, and finally the
`app_user` row itself. **Public provider directory data, network evidence, and any other user's
data are never touched** -- verified by `AccountDeletionTest`, which creates one row in every
user-owned table (via the real HTTP API, not direct DB inserts), creates a second untouched user
with their own saved provider, deletes the first account, and asserts: every one of the first
user's rows is gone, the provider record itself still exists, and the second user's saved-provider
row is unaffected.

**Frontend confirmation**: the Account page requires an explicit "Delete my account" click,
followed by a second, distinct "Yes, delete my account" confirmation with copy that states plainly
what's removed ("saved providers, shortlists, saved searches, navigator status, reminders, and
your saved plan") and what isn't ("Public provider records are not affected"). No countdown, no
"type your email to confirm" friction beyond the two-click confirmation -- deliberately simple
rather than manipulative-retention-pattern-adjacent (CLAUDE.md "Account Deletion UX": "No
manipulative retention copy").

**Not transactional as a single all-or-nothing unit today** -- each delete call is a separate
statement within `AuthService.deleteAccount`'s `@Transactional` method boundary, so in practice the
whole operation *is* one transaction (a failure partway through rolls back everything), but this
is a property of the method being `@Transactional`, not a design that was specifically hardened
against partial-failure edge cases beyond that.

## Why no separate "admin" path

There is deliberately no operator/admin endpoint for either export or deletion -- both are
self-service only, reachable exclusively through the authenticated user's own token. An operator
who needs to intervene on a specific account (e.g. a support request) does so directly against the
database, the same documented posture as directory-data-report review (`docs/directory-corrections.md`).
