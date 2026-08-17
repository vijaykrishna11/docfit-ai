import { useEffect, useState } from 'react'
import { fetchLocationSuggestions } from '../api/client'
import type { LocationSuggestionDto } from '../api/types'
import { MapPinIcon } from './icons'

interface SupportedAreasProps {
  onSelect: (location: string) => void
}

function SupportedAreas({ onSelect }: SupportedAreasProps) {
  const [cities, setCities] = useState<LocationSuggestionDto[]>([])

  useEffect(() => {
    let cancelled = false
    // An empty query returns a bounded, deduplicated set of real cities from the backend's
    // zip_geography table (one entry per city, never one per ZIP within it) -- CLAUDE.md
    // "Location Suggestions V3".
    fetchLocationSuggestions('')
      .then((results) => {
        if (!cancelled) setCities(results)
      })
      .catch(() => {
        if (!cancelled) setCities([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (cities.length === 0) {
    return null
  }

  return (
    <section className="page-section page-section-muted" aria-labelledby="supported-areas-heading">
      <div className="container">
        <h2 id="supported-areas-heading">Explore care near you</h2>
        <p className="section-intro">
          DocFit AI's provider data currently covers a partial set of Los Angeles County areas.
          Pick a city below to search that area directly.
        </p>
        <ul className="area-grid">
          {cities.map((city) => (
            <li key={`${city.city}-${city.stateCode}`} className="area-card">
              <button
                type="button"
                className="chip area-zip-chip"
                onClick={() => onSelect(`${city.city}, ${city.stateCode}`)}
              >
                <MapPinIcon width={16} height={16} />
                {city.city}, {city.stateCode}
              </button>
            </li>
          ))}
        </ul>
        <p className="field-hint">
          This is a partial, growing dataset -- not full Los Angeles County or nationwide coverage.
          Support for more areas may be added over time.
        </p>
      </div>
    </section>
  )
}

export default SupportedAreas
