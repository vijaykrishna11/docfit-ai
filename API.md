# DocFit AI API

Base URL (local): `http://localhost:8080`

All endpoints return JSON. Provider/reference-data search endpoints are public (no
authentication required) -- searching DocFit AI never requires an account. Endpoints under
`/api/auth`, `/api/saved-providers`, and `/api/saved-searches` require authentication (see
[Authentication](#authentication) below). CORS is restricted to the local Vite dev origins
(`http://localhost:5173`, `http://127.0.0.1:5173`), configurable via
`docfitai.cors.allowed-origins`, with `Access-Control-Allow-Credentials: true` so the browser
will send the refresh cookie on cross-origin requests from the frontend dev server.

Errors follow Spring Boot's default problem body shape, e.g.:
```json
{ "timestamp": "2026-08-15T04:19:14.292Z", "status": 400, "error": "Bad Request", "path": "/api/providers/search", "message": "Unknown specialty code." }
```
(`message` is included because `server.error.include-message=always` is set -- every message is
a hand-written, user-safe string, never a stack trace or internal exception detail.)

---

## `GET /api/specialties`

Lists the supported specialty groups.

**Example response**
```json
[
  { "code": "PRIMARY_CARE", "name": "Primary Care" },
  { "code": "CARDIOLOGY", "name": "Cardiology" },
  { "code": "DERMATOLOGY", "name": "Dermatology" },
  { "code": "ORTHOPEDICS", "name": "Orthopedics" },
  { "code": "PSYCHIATRY_MENTAL_HEALTH", "name": "Psychiatry / Mental Health" }
]
```

---

## `GET /api/insurance-carriers` (legacy)

Lists the demo insurance carrier options. **Informational only** -- there is no real
compatibility data behind this list, and it has no effect on provider search. Superseded by
`/api/insurance/payers` below; kept for backward compatibility, not used by the current frontend.

**Example response**
```json
[
  { "id": 1, "name": "Aetna" },
  { "id": 2, "name": "Anthem Blue Cross" }
]
```

---

## Insurance network intelligence

See `docs/insurance-network-architecture.md` for the full design. Summary: DocFit AI can show
**network directory evidence** for a small number of integrated payers -- never a coverage
guarantee. Selecting a payer/plan is entirely optional and never required to search.

### `GET /api/insurance/payers`

Lists payers DocFit AI knows about. `hasIntegratedPlans` distinguishes "known carrier name" from
"has real (or clearly-labeled synthetic demo) plan/network data" -- most payers are the former.

**Example response**
```json
[
  { "id": 1, "code": "AETNA", "name": "Aetna", "hasIntegratedPlans": false },
  { "id": 9, "code": "DOCFIT_DEMO", "name": "DocFit Demo Network (synthetic test data)", "hasIntegratedPlans": true }
]
```

### `GET /api/insurance/payers/{id}/plans`

Plans for a payer. Empty array if that payer has no integration. **404** for an unknown payer id.

**Example response**
```json
[
  { "id": 1, "payerId": 9, "planName": "DocFit Demo PPO (synthetic)", "planType": "PPO" }
]
```

### `GET /api/providers/{id}/network-evidence?planId=`

Full network evidence detail for one provider/plan pair.

| Param | Required | Description |
|---|---|---|
| `planId` | yes | An id from `/api/insurance/payers/{id}/plans` |
| `locationId` | no | A specific `provider_location` id (from a search result or provider detail's `location.id`). Evidence bound to a *different* location is never applied when this is omitted or doesn't match — see `docs/provider-data-platform.md` ("Network evidence + locations"). |

**404** if the provider doesn't exist. **400** if `planId` is missing or unknown.

**Example**: `GET /api/providers/1/network-evidence?planId=1`
```json
{
  "providerId": 1,
  "planId": 1,
  "planName": "DocFit Demo PPO (synthetic)",
  "networkName": "DocFit Demo Network Directory (synthetic)",
  "payerName": "DocFit Demo Network (synthetic test data)",
  "status": "EVIDENCE_FOUND",
  "freshness": "AGING",
  "matchedAddressLine1": null,
  "matchedCity": null,
  "matchedStateCode": null,
  "matchedPostalCode": null,
  "matchMethod": "NPI_EXACT",
  "sourceName": "DocFit synthetic demo evidence generator",
  "sourceType": "MANUAL_DEMO_REFERENCE",
  "synthetic": true,
  "checkedAt": "2026-07-04T07:28:59.932518Z",
  "firstSeenAt": "2026-08-16T07:28:59.942226Z",
  "limitations": [
    "Network directory participation may change and does not guarantee coverage or payment.",
    "Confirm eligibility and benefits directly with your insurer before your visit.",
    "Absence of evidence does not necessarily mean a provider is out of network -- directory data can be incomplete, stale, or specific to another location."
  ]
}
```
`status` is one of `EVIDENCE_FOUND`, `NO_EVIDENCE_FOUND`, `SOURCE_UNAVAILABLE`,
`MATCH_AMBIGUOUS`, `NOT_CHECKED` -- `NO_EVIDENCE_FOUND` never means "out of network." `synthetic`
is `true` only for the demo source; real sources would be `false`.

### `GET /api/providers/search` -- `planId` param

The existing search endpoint accepts an optional `planId`. When present, each result gets a
compact `networkEvidence` summary (`status`, `freshness`, `planName`, `networkName`, `synthetic`,
`checkedAt`) computed from locally stored evidence in one batched query -- never a live per-result
external call. Omitting `planId` is fully backward compatible: every existing required field is
unchanged, and `networkEvidence` is simply `null`. An unknown `planId` degrades gracefully
(search still succeeds; every result's `networkEvidence` is `null`).

---

## `GET /api/locations/suggestions`

Suggests locations from DocFit AI's local demo geography only (no external geocoder).

| Param | Required | Description |
|---|---|---|
| `q` | no | Free-text query; matches city name (contains, case-insensitive) or ZIP prefix. Empty returns the first few supported locations. |

**Example**: `GET /api/locations/suggestions?q=long`
```json
[
  { "zipCode": "90802", "city": "Long Beach", "stateCode": "CA", "label": "90802 — Long Beach, CA" },
  { "zipCode": "90803", "city": "Long Beach", "stateCode": "CA", "label": "90803 — Long Beach, CA" }
]
```

---

## `GET /api/providers/search`

Searches providers by specialty and location.

| Param | Required | Default | Description |
|---|---|---|---|
| `specialty` | yes | -- | One of the codes from `/api/specialties` |
| `zip` | one of `zip` / `location` / (`lat`+`lng`) required | -- | Exact 5-digit ZIP |
| `location` | | -- | Free text: a 5-digit ZIP, or a city name (optionally `"City, ST"`) |
| `lat`, `lng` | | -- | Coordinates (e.g. from browser geolocation); takes precedence over `zip`/`location` when both are present |
| `radius` | no | `25` | Search radius in miles |
| `sort` | no | `distance` | `distance`, `name`, or `name-desc` |
| `page` | no | `0` | Zero-based page index |
| `size` | no | `20` | Page size |
| `planId` | no | -- | An id from `/api/insurance/payers/{id}/plans`. See "Insurance network intelligence" below. |

**Example**: `GET /api/providers/search?specialty=CARDIOLOGY&location=90802&radius=25&sort=distance&page=0&size=20`
```json
{
  "results": [
    {
      "id": 57,
      "npiNumber": "1538111547",
      "entityType": "INDIVIDUAL",
      "firstName": "PARVATANENI",
      "lastName": "ARUN",
      "organizationName": null,
      "taxonomyCode": "207RC0000X",
      "specialtyDisplayName": "Cardiovascular Disease Specialist",
      "location": {
        "id": 57,
        "addressLine1": "2776 PACIFIC AVE",
        "addressLine2": null,
        "city": "LONG BEACH",
        "stateCode": "CA",
        "postalCode": "90806",
        "phone": "562-595-1911",
        "latitude": 33.806,
        "longitude": -118.182,
        "coordinatePrecision": "ZIP_CENTROID"
      },
      "distanceMiles": 2.5,
      "networkEvidence": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4,
  "totalPages": 1,
  "originLabel": "Long Beach, CA"
}
```
`originLabel` is `null` when the search origin came from raw `lat`/`lng` (no reverse geocoding
is performed). Every provider appears **once** per search, attached to its single nearest
qualifying practice location — a provider with multiple real offices (this is common; see
`docs/provider-ingestion.md`) is never repeated per office. `location.coordinatePrecision` is
truthful: DocFit AI's current data is always `ZIP_CENTROID` (a ZIP-centroid lookup), never
`EXACT`.

**400 Bad Request** when: `specialty` is unknown, no location is provided, or `zip`/`location`
doesn't resolve to a known demo-area location.

---

## `GET /api/providers/{id}`

Full detail for a single provider, including all of its taxonomy rows (not just the best
match used in search results) and all of its known practice locations.

| Param | Required | Description |
|---|---|---|
| `zip`, `location`, or `lat`+`lng` | no | If provided, `distanceMiles` is computed to the nearest location from this origin, and that location is returned as `location` (otherwise `location` is the provider's primary office and `distanceMiles` is `null`). |

**Example**: `GET /api/providers/57?location=90802`
```json
{
  "id": 57,
  "npiNumber": "1538111547",
  "entityType": "INDIVIDUAL",
  "firstName": "PARVATANENI",
  "lastName": "ARUN",
  "organizationName": null,
  "location": {
    "id": 57,
    "addressLine1": "2776 PACIFIC AVE",
    "addressLine2": null,
    "city": "LONG BEACH",
    "stateCode": "CA",
    "postalCode": "90806",
    "phone": "562-595-1911",
    "latitude": 33.806,
    "longitude": -118.182,
    "coordinatePrecision": "ZIP_CENTROID"
  },
  "otherLocations": [],
  "distanceMiles": 2.5,
  "taxonomies": [
    {
      "taxonomyCode": "207RC0000X",
      "classification": "Internal Medicine",
      "specialization": "Cardiovascular Disease",
      "displayName": "Cardiovascular Disease Specialist",
      "primaryTaxonomy": true
    }
  ]
}
```
`otherLocations` holds every other practice location DocFit AI knows about for this provider
(never duplicating `location`) — real, common example from this repo's own demo dataset: an
organization provider with **41** real NPPES-reported practice locations returns 1 in `location`
(the nearest to the search origin) and 40 in `otherLocations`.

**404 Not Found** when the provider ID doesn't exist.

Also includes `importedAt` (ISO-8601 timestamp) -- when this row was imported into DocFit AI,
populated exclusively by the database's `DEFAULT now()` at write time, never set from
application code. Shown on the detail page as "Imported into DocFit AI on...".

---

## `GET /api/providers/by-name`

Finds providers by name (first/last/organization), for users who already know who they're
looking for.

| Param | Required | Description |
|---|---|---|
| `q` | yes | Free-text name query, minimum 2 characters |

Returns at most 10 matches, each provider's best (primary) taxonomy only.

**Example**: `GET /api/providers/by-name?q=Arun`
```json
[
  {
    "id": 57,
    "npiNumber": "1538111547",
    "entityType": "INDIVIDUAL",
    "firstName": "PARVATANENI",
    "lastName": "ARUN",
    "organizationName": null,
    "city": "LONG BEACH",
    "stateCode": "CA",
    "specialtyDisplayName": "Cardiovascular Disease Specialist"
  }
]
```

---

## Authentication

DocFit AI accounts are optional and exist only to support saved providers and saved searches --
search itself never requires signing in.

**Design**
- Passwords are hashed with BCrypt; never stored or logged in plaintext.
- The **access token** is a short-lived JWT (HS256, 15 min default), returned in the JSON body
  only. The frontend keeps it in memory (a React context) and never writes it to `localStorage`
  or `sessionStorage`.
- The **refresh token** is a high-entropy opaque random value (not a JWT), sent as an `httpOnly`,
  `SameSite=Lax` cookie scoped to the `/api/auth` path only. The server stores only its SHA-256
  hash, never the raw value. Refreshing **rotates** the token (the old one is revoked, a new one
  issued); logging out revokes it immediately.
- Login/register are rate-limited per IP+email (in-memory sliding window; single-instance only,
  not distributed -- documented limitation, not a security gap for this deployment's scale).
- Login failures always return a generic "Invalid email or password." -- DocFit AI never reveals
  whether an email is registered.
- Every `/api/saved-providers` and `/api/saved-searches` request derives the current user from
  the authenticated principal (parsed from the JWT); a client-supplied user ID is never trusted.

### `POST /api/auth/register`
```json
{ "email": "jane@example.com", "password": "at-least-8-chars", "displayName": "Jane (optional)" }
```
**201 Created**, sets the refresh cookie, returns `{ accessToken, expiresInSeconds, user }`.
**409 Conflict** if the email is already registered.

### `POST /api/auth/login`
```json
{ "email": "jane@example.com", "password": "..." }
```
Same response shape as register. **401 Unauthorized** on bad credentials (generic message).

### `POST /api/auth/refresh`
No body; reads the refresh cookie. Rotates the refresh token and returns a new access token.
**401 Unauthorized** if the cookie is missing, expired, or already used/revoked.

### `POST /api/auth/logout`
No body; reads the refresh cookie, revokes it server-side, clears the cookie. **204 No Content**.

### `GET /api/auth/me`
Requires `Authorization: Bearer <accessToken>`. Returns the current user. **401** if missing/invalid/expired.

### `PATCH /api/auth/me`
```json
{ "displayName": "New name" }
```
Updates the display name. Requires authentication.

### `DELETE /api/auth/me`
Deletes the account, its saved providers, saved searches, and refresh tokens. Requires
authentication and explicit frontend confirmation before this is ever called.

---

## Saved providers

All endpoints require `Authorization: Bearer <accessToken>` and are scoped to the authenticated
user -- one user can never see or modify another user's saved providers (verified by dedicated
IDOR tests).

- `GET /api/saved-providers` -- list, most-recently-saved first. Each entry's `location` is the
  provider's primary practice location (nested `ProviderLocationDto`, same shape as search/detail)
  — there's no search origin here to pick a "nearest" one.
- `POST /api/saved-providers/{providerId}` -- save (idempotent; **204**). **404** if the provider
  doesn't exist.
- `DELETE /api/saved-providers/{providerId}` -- remove (**204**, idempotent).

---

## Saved searches

Same auth/ownership rules as saved providers. **Never created automatically** -- only in
response to an explicit user action.

- `GET /api/saved-searches` -- list, most-recently-saved first.
- `POST /api/saved-searches`
  ```json
  { "name": "optional", "specialtyCode": "CARDIOLOGY", "locationText": "90802", "radius": 25, "sort": "distance" }
  ```
  `latitude`/`longitude` may be sent instead of `locationText`. **400** if `specialtyCode` is
  unknown.
- `PATCH /api/saved-searches/{id}` -- rename (`{ "name": "..." }`). **404** if not found or not
  owned by the caller (existence is not confirmed to non-owners).
- `DELETE /api/saved-searches/{id}` -- remove. Same 404 semantics.
