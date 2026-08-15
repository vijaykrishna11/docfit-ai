import { useEffect, useMemo, useState } from 'react'
import { fetchLocationSuggestions } from '../api/client'
import type { LocationSuggestionDto } from '../api/types'
import { MapPinIcon } from './icons'

interface SupportedAreasProps {
  onSelect: (zipCode: string) => void
}

function SupportedAreas({ onSelect }: SupportedAreasProps) {
  const [locations, setLocations] = useState<LocationSuggestionDto[]>([])

  useEffect(() => {
    let cancelled = false
    // An empty query returns every supported demo-area location -- this is real reference
    // data from the backend's zip_geography table, not a hardcoded list.
    fetchLocationSuggestions('')
      .then((results) => {
        if (!cancelled) setLocations(results)
      })
      .catch(() => {
        if (!cancelled) setLocations([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  const groupedByCity = useMemo(() => {
    const groups = new Map<string, LocationSuggestionDto[]>()
    for (const location of locations) {
      const key = `${location.city}, ${location.stateCode}`
      const existing = groups.get(key) ?? []
      existing.push(location)
      groups.set(key, existing)
    }
    return Array.from(groups.entries())
  }, [locations])

  if (groupedByCity.length === 0) {
    return null
  }

  return (
    <section className="page-section page-section-muted" aria-labelledby="supported-areas-heading">
      <div className="container">
        <h2 id="supported-areas-heading">Explore care near you</h2>
        <p className="section-intro">
          DocFit AI currently covers a limited demo area. Pick a ZIP code below to search that
          location directly.
        </p>
        <ul className="area-grid">
          {groupedByCity.map(([cityLabel, cityLocations]) => (
            <li key={cityLabel} className="area-card">
              <h3>
                <MapPinIcon width={16} height={16} />
                {cityLabel}
              </h3>
              <div className="area-zip-list">
                {cityLocations.map((location) => (
                  <button
                    type="button"
                    key={location.zipCode}
                    className="chip area-zip-chip"
                    onClick={() => onSelect(location.zipCode)}
                  >
                    {location.zipCode}
                  </button>
                ))}
              </div>
            </li>
          ))}
        </ul>
        <p className="field-hint">
          This is demo coverage only -- not a nationwide directory. Support for more areas may be
          added over time.
        </p>
      </div>
    </section>
  )
}

export default SupportedAreas
