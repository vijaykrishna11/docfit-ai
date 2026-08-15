# Premium account & discovery pass — session notes

**Branch:** `feature/premium-account-and-discovery` (not merged to `main`)

**Commits on this branch (oldest → newest):**
1. `a19a829` — feat: add sort direction and location suggestions endpoint
2. `80b2d78` — feat: add provider detail experience, shareable search URLs, and comparison
3. `c4173bd` — docs: add DocFit AI project documentation
4. `a3ab1df` — style: add premium healthcare visual interactions
5. `c118e69` — feat: add secure DocFit account authentication *(backend)*
6. `82025d9` — feat: add frontend authentication, saved providers, and saved searches
7. `8f439fb` — feat: add provider name discovery, recently viewed, and compare persistence

Commits 1–4 predate this task and were already on the branch. Commits 5–7 are this session's
work: `c118e69` was the backend half (built earlier in this same session, before a context
summary), `82025d9` and `8f439fb` are the frontend half, built and verified after resuming.

---

## Auth design

- Passwords: BCrypt (`BCryptPasswordEncoder`), never logged or stored in plaintext.
- Access token: JWT (HS256, `io.jsonwebtoken` / JJWT 0.12.6 — a well-supported library, not
  hand-rolled crypto), 15-minute default TTL, returned in the JSON body only. The frontend
  (`AuthContext`) keeps it in a React state variable and mirrors it into a module-level variable
  in `api/client.ts` for request injection — **never** written to `localStorage` or
  `sessionStorage`.
- Refresh token: opaque high-entropy random value (32 bytes, `SecureRandom` + URL-safe Base64),
  **not** a JWT. Only its SHA-256 hash is stored server-side (fast, deterministic hashing is
  correct here since the token itself is already high-entropy — unlike a password, it doesn't
  need BCrypt's deliberate slowness). Delivered as an `httpOnly`, `SameSite=Lax` cookie scoped to
  `/api/auth`, 30-day default TTL.
- Rotation: every `/api/auth/refresh` call revokes the presented token and issues a new one
  (single-use). Logout revokes immediately. Verified end-to-end by both an integration test and a
  live curl session (old token rejected after rotation/logout).
- Session restore: on app mount, the frontend silently calls `/api/auth/refresh` once. If the
  browser has a valid refresh cookie, the user is signed back in with no visible flicker; if not,
  they're simply anonymous — no error is shown for the common "never logged in" case.
- CORS: a `CorsConfigurationSource` bean (not just `WebMvcConfigurer`, since Spring Security's
  filter chain runs before MVC) with `allowCredentials(true)` and an explicit origin allow-list —
  no wildcards, so the credentialed refresh cookie can only be sent from trusted origins.
- Rate limiting: in-memory sliding window keyed by `IP + normalized email` (not IP alone, so one
  NAT'd office can't lock out unrelated users; not email alone, so it also slows down credential
  stuffing against a single account). Explicitly documented as single-instance, not distributed —
  acceptable for this deployment's scale, called out as a known limitation below.
- Authorization boundary: every saved-provider/saved-search read or write derives the user from
  the authenticated principal parsed off the JWT (`AuthenticatedUser` record) — a client-supplied
  user ID is never trusted, never even accepted as a parameter.

## Database migrations

`V5__create_account_and_saved_data.sql` — adds `app_user`, `refresh_token`, `saved_provider`,
`saved_search`, and `ALTER TABLE provider ADD COLUMN imported_at TIMESTAMPTZ NOT NULL DEFAULT
now()`. The 271 existing provider rows got `now()` as their `imported_at` on migration — an
honest "we don't actually know when NPPES originally created this record, so we're recording when
DocFit AI imported it" value, never a fabricated historical date.

## Sign-in / register functionality

`/signin` and `/register`, split-screen layout, DocFit wordmark, no OAuth buttons, no "Forgot
password" link (not implemented, so not shown as if it were). Both surface real server error text
("An account with this email already exists.", "Invalid email or password.") via the backend's
`server.error.include-message=always` setting, with a generic fallback for network failures.
Registration validates password length client-side (≥8 chars) before ever calling the API.

## Saved-provider functionality

Heart-icon toggle on every `ProviderCard` and a labeled "Save provider" button on the detail
page (`SaveProviderButton`, shared component). Anonymous click → redirect to `/signin` with the
intended provider and return path preserved in the URL → completing sign-in/register saves the
provider and returns to the original page. `/saved` lists all saved providers with Call /
Directions / View details / Remove. Backed by `SavedProvidersContext`, which only fetches once
authenticated and clears on sign-out.

## Saved-search functionality

"Save this search" appears in the results toolbar **only when signed in** and only after a
successful search — never automatic, never a checkbox pre-checked by default. `/saved-searches`
lists saved searches with "Run search" (rebuilds the `/` query string) and "Remove."

## Privacy decisions

- Saved searches are opt-in only — confirmed by a dedicated backend IDOR test suite and by the
  UI never calling the save-search endpoint outside the explicit toolbar button.
- Recently-viewed providers live in `sessionStorage` only (never sent to the server, cleared by
  the user or when the tab closes) — this is a browser convenience, not an account feature.
- Compare selection persists in `sessionStorage` for the same reason (survive an accidental
  reload), also never sent anywhere.
- Geolocation coordinates, when used, still appear in shareable search URLs at full precision —
  unchanged from the prior session's flagged decision, still worth a product-owner call on
  whether to round them.

## Unique features added this pass

- Provider name search ("Already know who you're looking for?") — debounced, backed by
  `GET /api/providers/by-name`.
- Data provenance on the detail page ("Imported into DocFit AI on...") from the real
  `imported_at` column.
- Share button (Clipboard API) on the provider detail page, alongside the existing search-share
  button.

## Frontend routes

`/`, `/providers/:id`, `/compare`, `/signin`, `/register`, `/account` (protected),
`/saved` (protected), `/saved-searches` (protected), `*` → 404 page.

## Backend endpoints (new this pass)

`POST /api/auth/{register,login,refresh,logout}`, `GET/PATCH/DELETE /api/auth/me`,
`GET/POST/DELETE /api/saved-providers[/{id}]`, `GET/POST/PATCH/DELETE /api/saved-searches[/{id}]`,
`GET /api/providers/by-name`. Full request/response shapes documented in `API.md`.

## Tests added

- Backend: `AuthControllerTest` (6 — register/duplicate/login/wrong-password/refresh-rotation/
  logout-revocation/me), `SavedProviderAuthorizationTest` (3, including a live IDOR attempt),
  `SavedSearchAuthorizationTest` (2, including a live IDOR attempt).
- Frontend: `SignInPage.test.tsx` (3), `RegisterPage.test.tsx` (3), `ProtectedRoute.test.tsx` (2),
  `SaveProviderButton.test.tsx` (2, covering both the anonymous-redirect and authenticated-save
  paths).

## Test results

- Backend: `./mvnw --batch-mode verify` → **35/35 passing**, BUILD SUCCESS.
- Frontend: `npm run typecheck` clean, `npm run lint` clean (3 pre-existing fast-refresh warnings
  on context files, not new problems), `npm run test` → **24/24 passing**, `npm run build` →
  succeeds (297 kB JS / 89 kB gzip, 31.5 kB CSS / 6 kB gzip).

## E2E result

No browser-automation tool was available in this session (checked; none registered). In its
place, a full curl-driven pass against the actually-running backend + Postgres verified:
register (with the real frontend `Origin` header, confirming CORS + credentialed cookie both
work), login, `/me`, save a real provider, a second registered user attempting to delete the
first user's saved provider (**IDOR blocked** — no-op, first user's data intact), refresh token
rotation, logout revocation (post-logout refresh correctly rejected), anonymous access to
`/api/saved-providers` correctly rejected (401), and anonymous provider search still working
(200). All frontend logic paths (sign-in, register, protected-route redirect, anonymous vs.
authenticated save) are additionally covered by the 10 new Testing-Library tests above, which do
exercise the real component tree. **A manual browser click-through pass is still recommended**
when a browser is available.

## Security findings

- One real bug found and fixed during this session: `AuthController` was passing a hardcoded
  literal (`"register"` / `"login"`) as the rate-limit key instead of a real per-client
  identifier, meaning the rate limit bucket was shared across every user rather than being
  per-IP+email. Fixed by deriving the key from the actual client IP + normalized email inside
  `AuthService`. No other findings.

## Accessibility findings

Followed the existing codebase's conventions (labeled inputs, `aria-live`, `aria-pressed` on
toggles, `aria-expanded`/`aria-haspopup` on the account menu, focus-visible states, Escape-to-
close on the account dropdown and mobile menu). No dedicated automated a11y audit (e.g. axe) was
run this session — same gap as the prior session's notes; still worth a formal pass.

## Performance / bundle impact

Production bundle grew from 262.7 kB → 293.97 kB JS (before this pass's discovery features) →
297.32 kB JS / 89.13 kB gzip with the full auth + saved-data + discovery feature set; CSS grew
from ~20 kB to 31.5 kB / 6 kB gzip. Still a single small bundle, no code-splitting needed at this
size.

## Known limitations

- Rate limiting is in-memory/single-instance, not distributed — fine for one backend instance,
  would need a shared store (e.g. Redis) behind a load balancer. Documented, not hidden.
- No password-reset flow — the UI reflects this honestly (no dead "Forgot password" link) rather
  than promising something unbuilt.
- No formal accessibility audit tool was run.
- No live browser E2E (no browser-automation tool available) — substituted with curl-driven
  backend E2E plus the full automated frontend test suite.
- Geolocation coordinates remain unrounded in shareable URLs — flagged for product-owner review,
  not resolved.

## Features skipped, and why

- **Map/list view (Leaflet)** — skipped. The demo geography is only 6 ZIP centroids; a map would
  visually imply house-level precision the data doesn't have. Not worth the honesty risk for this
  dataset's scale.
- **Filter drawer / advanced taxonomy filter** — skipped. The existing filter-chip + toolbar
  pattern already covers the current filter surface (specialty, location, radius); a drawer would
  be premature UI complexity for the number of filters that actually exist.
- **Command palette** — skipped. Low value at this app's size (8 routes); would be pure novelty.
- **First-visit product tour** — skipped. The homepage's How It Works / Data Sources / About
  sections and inline empty-state copy already explain the product; a tour would be redundant.
- **Favorite-specialties dashboard** — skipped as a separate feature; saved searches already
  cover "get back to a specialty I care about" without a second, overlapping mechanism.
- **Dedicated `/data` and `/how-it-works` routes** — skipped; this content already lives on the
  homepage as anchored sections and splitting it into separate routes wouldn't add real value.
- **Playwright E2E** — not newly introduced (wasn't configured in the repo before this session,
  and the instructions said to extend it only if it already existed).

Nothing above was skipped for being "risky" in the sense of the stop-conditions — these were
deliberate, documented scope calls, not abandoned or blocked work.

## Branch push result

Not yet pushed as of writing this file — will push after this final documentation commit, per the
session's git-safety instructions (create commits, push the feature branch; never merge to
`main`).

## Suggested next 10 improvements

1. Manual browser click-through QA (sign-in, save-provider redirect flow, account menu, mobile
   viewport) once a browser/screenshot tool is available — the one meaningful verification gap
   this session couldn't close itself.
2. Formal accessibility audit (axe or similar) across the new auth/account pages.
3. Decide on rounding/obfuscating geolocation coordinates in shareable search URLs (flagged,
   unresolved, across two sessions now).
4. Real password-reset flow (email-based), if/when an email-sending service is approved.
5. Distributed rate limiting (Redis-backed) if DocFit AI ever runs more than one backend
   instance.
6. Expand demo geography beyond the current 6 Long Beach-area ZIPs.
7. Real insurance-compatibility data source (currently explicitly informational-only).
8. A lightweight, honest map view once the underlying geography data has real street-level
   precision to show (currently ZIP-centroid only).
9. "Why this result?" factual explainability panel (e.g. "matched your specialty search," "within
   your 25-mile radius") — scoped out of this pass for time, still a good small follow-up.
10. Rename/organize saved searches beyond the current flat list, if usage shows people saving
    more than a handful.
