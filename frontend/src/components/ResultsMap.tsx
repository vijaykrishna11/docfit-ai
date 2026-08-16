import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { PRECISE_COORDINATE_PRECISIONS, type ProviderSearchResultDto } from '../api/types'
import { useAuth } from '../context/AuthContext'
import { useCompare } from '../context/CompareContext'
import { useSavedProviders } from '../context/SavedProvidersContext'
import { formatDistance, formattedAddress, providerDisplayName } from '../utils/providerDisplay'

interface ResultsMapProps {
  results: ProviderSearchResultDto[]
  selectedProviderId: number | null
  onSelectProvider: (id: number | null) => void
  originLabel?: string | null
}

const DEFAULT_CENTER: [number, number] = [39.8283, -98.5795] // contiguous US centroid -- only used when no result has coordinates yet
const DEFAULT_ZOOM = 4

function buildIcon(precise: boolean, selected: boolean): L.DivIcon {
  const size = selected ? 22 : 16
  const className = `map-marker-icon ${precise ? 'map-marker-precise' : 'map-marker-approximate'}${selected ? ' map-marker-selected' : ''}`
  return L.divIcon({
    className: '',
    html: `<span class="${className}" aria-hidden="true"></span>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
}

interface PopupHandlers {
  onViewDetails: (providerId: number) => void
  onToggleSave: (providerId: number) => void
  onToggleCompare: (providerId: number) => void
  isAuthenticated: () => boolean
}

function buildPopupContent(result: ProviderSearchResultDto, handlers: PopupHandlers): HTMLElement {
  const container = document.createElement('div')
  container.className = 'map-popup'

  const name = document.createElement('p')
  name.className = 'map-popup-name'
  name.textContent = providerDisplayName(result)
  container.appendChild(name)

  const specialty = document.createElement('p')
  specialty.className = 'map-popup-specialty'
  specialty.textContent = result.specialtyDisplayName
  container.appendChild(specialty)

  const { line1, line2 } = formattedAddress(result.location)
  const address = document.createElement('p')
  address.className = 'map-popup-address'
  address.textContent = `${formatDistance(result.distanceMiles)} • ${line1}, ${line2}`
  container.appendChild(address)

  const phone = document.createElement('p')
  phone.className = 'map-popup-phone'
  phone.textContent = result.location.phone ? 'Phone available' : 'No phone on file'
  container.appendChild(phone)

  const actions = document.createElement('div')
  actions.className = 'map-popup-actions'

  const viewDetailsButton = document.createElement('button')
  viewDetailsButton.type = 'button'
  viewDetailsButton.className = 'ghost-button'
  viewDetailsButton.textContent = 'View details'
  viewDetailsButton.addEventListener('click', () => handlers.onViewDetails(result.id))
  actions.appendChild(viewDetailsButton)

  if (handlers.isAuthenticated()) {
    const saveButton = document.createElement('button')
    saveButton.type = 'button'
    saveButton.className = 'ghost-button'
    saveButton.textContent = 'Save'
    saveButton.addEventListener('click', () => handlers.onToggleSave(result.id))
    actions.appendChild(saveButton)
  }

  const compareButton = document.createElement('button')
  compareButton.type = 'button'
  compareButton.className = 'ghost-button'
  compareButton.textContent = 'Compare'
  compareButton.addEventListener('click', () => handlers.onToggleCompare(result.id))
  actions.appendChild(compareButton)

  container.appendChild(actions)
  return container
}

/**
 * Lazy-loaded (see HomePage's dynamic import) -- users who never open the map shouldn't pay for
 * Leaflet in their initial bundle. Precision-aware markers (CLAUDE.md "Map Accuracy Rules"): a
 * solid pin for a real geocode, a hollow/soft marker for a ZIP/city-centroid approximation, so an
 * approximate location never looks like an exact office entrance.
 *
 * Save/Compare/View-details handlers are read through refs (updated by a separate, cheap effect)
 * rather than being marker-rebuild dependencies -- toggling a save is not a reason to tear down
 * and recreate every marker on the map (CLAUDE.md "Do not create annoying map motion").
 */
function ResultsMap({ results, selectedProviderId, onSelectProvider, originLabel }: ResultsMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const markersRef = useRef<globalThis.Map<number, L.Marker>>(new globalThis.Map())
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const { toggleSaved } = useSavedProviders()
  const { toggle: toggleCompare } = useCompare()

  const handlersRef = useRef<PopupHandlers>({
    onViewDetails: () => {},
    onToggleSave: () => {},
    onToggleCompare: () => {},
    isAuthenticated: () => false,
  })
  useEffect(() => {
    handlersRef.current = {
      onViewDetails: (providerId) => navigate(`/providers/${providerId}`),
      onToggleSave: (providerId) => void toggleSaved(providerId),
      onToggleCompare: (providerId) => toggleCompare(providerId),
      isAuthenticated: () => isAuthenticated,
    }
  }, [navigate, toggleSaved, toggleCompare, isAuthenticated])

  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return
    const map = L.map(mapContainerRef.current, { scrollWheelZoom: false }).setView(DEFAULT_CENTER, DEFAULT_ZOOM)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(map)
    mapRef.current = map
    return () => {
      map.remove()
      mapRef.current = null
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return

    markersRef.current.forEach((marker) => marker.remove())
    markersRef.current.clear()

    const validResults = results.filter((result) => {
      const lat = result.location.latitude == null ? null : Number(result.location.latitude)
      const lng = result.location.longitude == null ? null : Number(result.location.longitude)
      return lat != null && lng != null && Number.isFinite(lat) && Number.isFinite(lng)
    })

    validResults.forEach((result) => {
      const lat = Number(result.location.latitude)
      const lng = Number(result.location.longitude)
      const precise = PRECISE_COORDINATE_PRECISIONS.has(result.location.coordinatePrecision)
      const marker = L.marker([lat, lng], { icon: buildIcon(precise, result.id === selectedProviderId) })
      marker.on('click', () => onSelectProvider(result.id))
      marker.bindPopup(
        buildPopupContent(result, {
          onViewDetails: (id) => handlersRef.current.onViewDetails(id),
          onToggleSave: (id) => handlersRef.current.onToggleSave(id),
          onToggleCompare: (id) => handlersRef.current.onToggleCompare(id),
          isAuthenticated: () => handlersRef.current.isAuthenticated(),
        }),
      )
      marker.addTo(map)
      markersRef.current.set(result.id, marker)
    })

    if (validResults.length > 0) {
      const bounds = L.latLngBounds(
        validResults.map(
          (result) => [Number(result.location.latitude), Number(result.location.longitude)] as [number, number],
        ),
      )
      map.fitBounds(bounds, { padding: [48, 48], maxZoom: 14 })
    }
    // selectedProviderId intentionally omitted: handled by the lightweight effect below so
    // selecting a result never tears down and rebuilds every marker on the map.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results, onSelectProvider])

  useEffect(() => {
    markersRef.current.forEach((marker, providerId) => {
      const result = results.find((r) => r.id === providerId)
      if (!result) return
      const precise = PRECISE_COORDINATE_PRECISIONS.has(result.location.coordinatePrecision)
      marker.setIcon(buildIcon(precise, providerId === selectedProviderId))
    })
    if (selectedProviderId != null) {
      markersRef.current.get(selectedProviderId)?.openPopup()
    }
  }, [selectedProviderId, results])

  return (
    <div
      className="results-map"
      ref={mapContainerRef}
      role="region"
      aria-label={`Map of search results${originLabel ? ` near ${originLabel}` : ''}`}
    />
  )
}

export default ResultsMap
