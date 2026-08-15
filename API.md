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

## `GET /api/insurance-carriers`

Lists the demo insurance carrier options. **Informational only** -- there is no real
compatibility data behind this list, and it has no effect on provider search.

**Example response**
```json
[
  { "id": 1, "name": "Aetna" },
  { "id": 2, "name": "Anthem Blue Cross" }
]
```

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

**Example**: `GET /api/providers/search?specialty=CARDIOLOGY&location=90802&radius=25&sort=distance&page=0&size=20`
```json
{
  "results": [
    {
      "id": 57,
      "npiNumber": "1538111547",
      "firstName": "PARVATANENI",
      "lastName": "ARUN",
      "organizationName": null,
      "phone": "562-595-1911",
      "addressLine1": "2776 PACIFIC AVE",
      "addressLine2": null,
      "city": "LONG BEACH",
      "stateCode": "CA",
      "postalCode": "90806",
      "taxonomyCode": "207RC0000X",
      "specialtyDisplayName": "Cardiovascular Disease Specialist",
      "distanceMiles": 2.5
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
is performed).

**400 Bad Request** when: `specialty` is unknown, no location is provided, or `zip`/`location`
doesn't resolve to a known demo-area location.

---

## `GET /api/providers/{id}`

Full detail for a single provider, including all of its taxonomy rows (not just the best
match used in search results).

| Param | Required | Description |
|---|---|---|
| `zip`, `location`, or `lat`+`lng` | no | If provided, `distanceMiles` is computed from this origin (e.g. to show "distance from your search" on the detail page); otherwise `distanceMiles` is `null`. |

**Example**: `GET /api/providers/57?location=90802`
```json
{
  "id": 57,
  "npiNumber": "1538111547",
  "firstName": "PARVATANENI",
  "lastName": "ARUN",
  "organizationName": null,
  "phone": "562-595-1911",
  "addressLine1": "2776 PACIFIC AVE",
  "addressLine2": null,
  "city": "LONG BEACH",
  "stateCode": "CA",
  "postalCode": "90806",
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

- `GET /api/saved-providers` -- list, most-recently-saved first.
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
