import { useState, type FormEvent } from 'react'
import type { InsuranceCarrierDto, SpecialtyDto } from '../api/types'
import { useGeolocation } from '../hooks/useGeolocation'
import { CheckIcon, CloseIcon, CrosshairIcon, InsuranceIcon, LocationIcon, SearchIcon, SpecialtyIcon } from './icons'

export interface SearchFormValues {
  specialty: string
  insuranceCarrierId: string
  radius: number
  location?: string
  lat?: number
  lng?: number
}

const RADIUS_OPTIONS = [5, 10, 25, 50] as const

interface SearchFormProps {
  specialties: SpecialtyDto[]
  insuranceCarriers: InsuranceCarrierDto[]
  onSearch: (values: SearchFormValues) => void
  disabled?: boolean
}

function SearchForm({ specialties, insuranceCarriers, onSearch, disabled = false }: SearchFormProps) {
  const [specialty, setSpecialty] = useState('')
  const [insuranceCarrierId, setInsuranceCarrierId] = useState('')
  const [locationText, setLocationText] = useState('')
  const [radius, setRadius] = useState(25)
  const geolocation = useGeolocation()

  const usingCurrentLocation = geolocation.status === 'granted' && geolocation.coords != null

  function handleClearLocation() {
    geolocation.reset()
    setLocationText('')
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (usingCurrentLocation && geolocation.coords) {
      onSearch({ specialty, insuranceCarrierId, radius, lat: geolocation.coords.lat, lng: geolocation.coords.lng })
    } else {
      onSearch({ specialty, insuranceCarrierId, radius, location: locationText })
    }
  }

  return (
    <form className="search-panel" onSubmit={handleSubmit} aria-label="Provider search">
      <div className="field">
        <label htmlFor="specialty">Specialty</label>
        <div className="input-with-icon">
          <SpecialtyIcon width={18} height={18} />
          <select
            id="specialty"
            value={specialty}
            onChange={(event) => setSpecialty(event.target.value)}
            required
          >
            <option value="" disabled>
              Select a specialty
            </option>
            {specialties.map((s) => (
              <option key={s.code} value={s.code}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="field">
        <label htmlFor="insurance">Insurance</label>
        <div className="input-with-icon">
          <InsuranceIcon width={18} height={18} />
          <select
            id="insurance"
            value={insuranceCarrierId}
            onChange={(event) => setInsuranceCarrierId(event.target.value)}
          >
            <option value="">Any / not sure</option>
            {insuranceCarriers.map((carrier) => (
              <option key={carrier.id} value={carrier.id}>
                {carrier.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="field field-location">
        <label htmlFor="location">Location</label>
        <div className="input-with-icon">
          {usingCurrentLocation ? <CrosshairIcon width={18} height={18} /> : <LocationIcon width={18} height={18} />}
          <input
            id="location"
            name="location"
            type="text"
            placeholder="ZIP, city, or city, state"
            value={usingCurrentLocation ? 'Current location' : locationText}
            onChange={(event) => setLocationText(event.target.value)}
            readOnly={usingCurrentLocation}
            required={!usingCurrentLocation}
          />
          {usingCurrentLocation && (
            <button
              type="button"
              className="input-clear-button"
              onClick={handleClearLocation}
              aria-label="Clear current location"
            >
              <CloseIcon width={13} height={13} />
            </button>
          )}
        </div>
        <button
          type="button"
          className={`use-location-button${geolocation.status === 'requesting' ? ' is-loading' : ''}${
            usingCurrentLocation ? ' is-granted' : ''
          }`}
          onClick={geolocation.request}
          disabled={geolocation.status === 'requesting'}
        >
          {usingCurrentLocation ? (
            <CheckIcon width={15} height={15} />
          ) : (
            <CrosshairIcon width={15} height={15} />
          )}
          {geolocation.status === 'requesting'
            ? 'Getting location…'
            : usingCurrentLocation
              ? 'Using your location'
              : 'Use my location'}
        </button>
        {geolocation.errorMessage && (
          <p className="field-hint field-hint-error" role="alert">
            {geolocation.errorMessage}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor="radius">Radius</label>
        <select
          id="radius"
          className="plain-select"
          value={radius}
          onChange={(event) => setRadius(Number(event.target.value))}
        >
          {RADIUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option} miles
            </option>
          ))}
        </select>
      </div>

      <button type="submit" className="primary-button search-cta" disabled={disabled}>
        <SearchIcon width={18} height={18} />
        {disabled ? 'Searching…' : 'Find providers'}
      </button>
    </form>
  )
}

export default SearchForm
