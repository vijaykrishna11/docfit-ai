# Map and location accuracy

How the discovery map represents location precision honestly, and how the list/map layout works.
Companion to `docs/geospatial-scaling.md` (backend distance/bounding-box implementation) and
`docs/provider-ingestion.md` (where `coordinatePrecision` values come from).

## Technology

[Leaflet](https://leafletjs.com/) + OpenStreetMap tiles (`{s}.tile.openstreetmap.org`). No paid
map API, no API key. `ResultsMap.tsx` uses plain Leaflet directly (not `react-leaflet`) so marker
styling and popup content have full, direct control -- popups are built via
`document.createElement`/`.textContent`, never `innerHTML`, so provider-controlled text (names,
addresses) can never inject markup.

**Production tile usage note**: OpenStreetMap's public tile servers are appropriate for
development/beta-scale traffic under their [tile usage policy](https://operations.osmfoundation.org/policies/tiles/).
At real production scale, switch to a dedicated tile provider (e.g. a paid OSM-tile CDN) rather
than relying on the public servers indefinitely -- tracked in
`docs/production-deployment-plan.md` as a scale-dependent follow-up, not done preemptively.

## Marker precision (CLAUDE.md "Map Accuracy Rules")

DocFit AI's `coordinatePrecision` field (`EXACT`, `ADDRESS_GEOCODE`, `ZIP_CENTROID`,
`CITY_CENTROID`, `UNKNOWN`) is never overridden or guessed for map display:

- **Precise** (`EXACT`/`ADDRESS_GEOCODE`): a solid, filled teal pin (`.map-marker-precise`).
- **Approximate** (`ZIP_CENTROID`/`CITY_CENTROID`/`UNKNOWN`): a hollow, dashed-outline marker
  (`.map-marker-approximate`) -- deliberately never looks like a precise office-entrance pin.

A caption below the map always reads: "Some map locations are approximate because the public
provider record does not include precise geocoded coordinates." The same honesty applies outside
the map -- `LocationPrecisionNote` on the provider card/detail page renders the identical message
wherever a ZIP/city-centroid location is shown, map or no map.

As of this phase, DocFit AI's actual imported data is consistently `ZIP_CENTROID` (see
`docs/provider-ingestion.md`) -- there is no live address-geocoding step yet, so every marker
today renders as the approximate (hollow) style. The precise/approximate distinction in the code is
real and tested (`ResultsMap`'s `buildIcon`), not dead code -- it activates automatically the
moment address-level geocoding is added, without a UI change.

## List/map layout

- **Desktop** (`≥ 900px`): persistent split, list ~45% / map ~55% (`.results-layout`,
  `.results-list-pane`, `.results-map-pane`), both always visible together.
- **Mobile** (`< 900px`): a List/Map segmented toggle (`.results-view-toggle`); only one pane is
  shown at a time, never a forced split-screen. The hidden pane stays mounted in the DOM (not
  destroyed), so toggling back to Map doesn't re-fetch or re-render from scratch.
- **"Skip map"**: a visually-hidden-until-focused link before the map region, so a keyboard user
  can jump straight to the results list without tabbing through map controls.
- **Never the only way in**: every action available from a map marker's popup (view details, save,
  compare) is also available directly on the corresponding list card -- the map is a convenience
  view, not a required one. Verified in `e2e/accessibility.spec.ts` and `e2e/map-and-filters.spec.ts`.

## Marker interaction

- **fitBounds on load**: the map fits its viewport to all valid-coordinate results (invalid/null
  coordinates are filtered out before computing bounds, so one malformed row can't zoom the map
  out to the whole country).
- **Click marker → popup**: name, specialty, distance, address, phone-availability (never fake
  availability), and View details/Save/Compare actions. Clicking a marker also highlights its
  matching list card (`.provider-card.is-map-selected`).
- **Click card → marker**: clicking anywhere on a list card selects that provider, opening/
  highlighting its marker. Hover-to-highlight (optional per spec) was not implemented this phase --
  click-based sync covers the explicit requirement.
- **No annoying motion**: marker icons and popup content update via a lightweight ref-based
  handler pattern specifically so that routine actions (saving a provider, toggling compare) never
  tear down and rebuild every marker on the map -- see `ResultsMap.tsx`'s comments for the exact
  mechanism and the bug it was written to avoid.

## Performance

Markers render only for the current page of results (bounded, paginated -- see
`docs/geospatial-scaling.md`), never an unbounded live query. No marker clustering library is
included; at current page sizes (max 200 per `MAX_PAGE_SIZE`) this hasn't been needed. If a future
phase allows showing more results on the map simultaneously, add a lightweight Leaflet clustering
plugin then, not preemptively.

## Bundle size (code splitting)

Leaflet is lazy-loaded (`React.lazy(() => import('./ResultsMap'))`) -- a user who never opens the
map pays nothing for it in the initial bundle. Measured via `npm run build`:

| | Main bundle (JS, gzip) | Map chunk (JS, gzip) |
|---|---|---|
| Before this phase | 316.14 kB / 93.88 kB | -- |
| After (map lazy-loaded) | ~325 kB / ~96 kB | 152.63 kB / 44.83 kB (separate chunk, own CSS: 15.09 kB / 6.36 kB gzip) |

The map chunk only downloads when a search actually returns results and the map pane renders --
never on the bare homepage.
