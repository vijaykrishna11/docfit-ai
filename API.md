# DocFit AI API

Base URL (local): `http://localhost:8080`

All endpoints are read-only `GET` requests and return JSON. There is no authentication --
this is a demo API with no user accounts. CORS is restricted to the local Vite dev origins
(`http://localhost:5173`, `http://127.0.0.1:5173`), configurable via
`docfitai.cors.allowed-origins`.

Errors follow Spring Boot's default problem body shape, e.g.:
```json
{ "timestamp": "2026-08-15T04:19:14.292Z", "status": 400, "error": "Bad Request", "path": "/api/providers/search" }
```

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
