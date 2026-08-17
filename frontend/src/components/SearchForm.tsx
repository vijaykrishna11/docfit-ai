import { useEffect, useState, type FormEvent } from 'react'
import { fetchLocationSuggestions } from '../api/client'
import type { LocationSuggestionDto, PayerDto, SpecialtyDto } from '../api/types'
import { useGeolocation } from '../hooks/useGeolocation'
import InsurancePlanSelector, { type InsurancePlanSelectorValue } from './InsurancePlanSelector'
import { CheckIcon, CloseIcon, CrosshairIcon, LocationIcon, SearchIcon, SpecialtyIcon } from './icons'

export interface SearchFormValues {
  specialty: string
  payerId: string
  planId: string
  radius: number
  location?: string
  lat?: number
  lng?: number
}

export interface SearchFormInitialValues {
  specialty?: string
  payerId?: string
  planId?: string
  location?: string
  radius?: number
}

const RADIUS_OPTIONS = [5, 10, 25, 50] as const

// Presentational grouping only -- the specialty list itself (codes/names/descriptions) always
// comes from the API, never duplicated here (CLAUDE.md "Shared Specialty Type"). A code with no
// entry here falls into "Other" rather than being silently hidden, so a new backend specialty
// always remains selectable even before this map is updated.
const SPECIALTY_GROUP_ORDER = ['Primary care', 'Medical specialties', 'Surgical specialties', 'Behavioral health', 'Other'] as const
const SPECIALTY_GROUPS: Record<string, (typeof SPECIALTY_GROUP_ORDER)[number]> = {
  PRIMARY_CARE: 'Primary care',
  PEDIATRICS: 'Primary care',
  OBSTETRICS_GYNECOLOGY: 'Primary care',
  CARDIOLOGY: 'Medical specialties',
  DERMATOLOGY: 'Medical specialties',
  NEUROLOGY: 'Medical specialties',
  GASTROENTEROLOGY: 'Medical specialties',
  ENDOCRINOLOGY: 'Medical specialties',
  PULMONOLOGY: 'Medical specialties',
  NEPHROLOGY: 'Medical specialties',
  UROLOGY: 'Medical specialties',
  OPHTHALMOLOGY: 'Medical specialties',
  OTOLARYNGOLOGY: 'Medical specialties',
  ALLERGY_IMMUNOLOGY: 'Medical specialties',
  RHEUMATOLOGY: 'Medical specialties',
  ORTHOPEDICS: 'Surgical specialties',
  GENERAL_SURGERY: 'Surgical specialties',
  PSYCHIATRY_MENTAL_HEALTH: 'Behavioral health',
  PHYSICAL_MEDICINE_REHAB: 'Other',
}

function groupSpecialties(specialties: SpecialtyDto[]): { group: string; items: SpecialtyDto[] }[] {
  const byGroup = new Map<string, SpecialtyDto[]>()
  for (const specialty of specialties) {
    const group = SPECIALTY_GROUPS[specialty.code] ?? 'Other'
    const items = byGroup.get(group) ?? []
    items.push(specialty)
    byGroup.set(group, items)
  }
  return SPECIALTY_GROUP_ORDER.filter((group) => byGroup.has(group)).map((group) => ({
    group,
    items: byGroup.get(group)!,
  }))
}

interface SearchFormProps {
  specialties: SpecialtyDto[]
  payers: PayerDto[]
  onSearch: (values: SearchFormValues) => void
  disabled?: boolean
  initialValues?: SearchFormInitialValues
}

function SearchForm({ specialties, payers, onSearch, disabled = false, initialValues }: SearchFormProps) {
  const [specialty, setSpecialty] = useState(initialValues?.specialty ?? '')
  const [insurance, setInsurance] = useState<InsurancePlanSelectorValue>({
    payerId: initialValues?.payerId ?? '',
    planId: initialValues?.planId ?? '',
  })
  const [locationText, setLocationText] = useState(initialValues?.location ?? '')
  const [radius, setRadius] = useState(initialValues?.radius ?? 25)
  const [locationSuggestions, setLocationSuggestions] = useState<LocationSuggestionDto[]>([])
  const geolocation = useGeolocation()

  const usingCurrentLocation = geolocation.status === 'granted' && geolocation.coords != null
  const [justFoundLocation, setJustFoundLocation] = useState(false)

  useEffect(() => {
    if (geolocation.status !== 'granted') {
      return
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect -- brief confirmation flash on success
    setJustFoundLocation(true)
    const timeoutId = window.setTimeout(() => setJustFoundLocation(false), 1600)
    return () => window.clearTimeout(timeoutId)
  }, [geolocation.status])

  useEffect(() => {
    if (usingCurrentLocation || locationText.trim().length === 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- clears suggestions on empty query
      setLocationSuggestions([])
      return
    }
    let cancelled = false
    const timeoutId = window.setTimeout(() => {
      fetchLocationSuggestions(locationText.trim())
        .then((suggestions) => {
          if (!cancelled) setLocationSuggestions(suggestions)
        })
        .catch(() => {
          if (!cancelled) setLocationSuggestions([])
        })
    }, 200)
    return () => {
      cancelled = true
      window.clearTimeout(timeoutId)
    }
  }, [locationText, usingCurrentLocation])

  function handleClearLocation() {
    geolocation.reset()
    setLocationText('')
  }

  function resolveLocationValue(): string {
    const matchedSuggestion = locationSuggestions.find((suggestion) => suggestion.label === locationText)
    if (!matchedSuggestion) {
      return locationText
    }
    // A CITY suggestion has no single zipCode (it's deduplicated across every ZIP that shares the
    // city) -- search by "city, state" text instead, which the backend already resolves to a
    // city-wide centroid (CoordinatePrecision.CITY_CENTROID), never a fabricated single ZIP point.
    if (matchedSuggestion.type === 'CITY') {
      return `${matchedSuggestion.city}, ${matchedSuggestion.stateCode}`
    }
    return matchedSuggestion.zipCode ?? locationText
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const insuranceValues = { payerId: insurance.payerId, planId: insurance.planId }
    if (usingCurrentLocation && geolocation.coords) {
      onSearch({ specialty, ...insuranceValues, radius, lat: geolocation.coords.lat, lng: geolocation.coords.lng })
    } else {
      onSearch({ specialty, ...insuranceValues, radius, location: resolveLocationValue() })
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
            {groupSpecialties(specialties).map(({ group, items }) => (
              <optgroup key={group} label={group}>
                {items.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </div>
      </div>

      <InsurancePlanSelector payers={payers} value={insurance} onChange={setInsurance} />

      <div className="field field-location">
        <label htmlFor="location">Location</label>
        <div className="input-with-icon">
          {usingCurrentLocation ? <CrosshairIcon width={18} height={18} /> : <LocationIcon width={18} height={18} />}
          <input
            id="location"
            name="location"
            type="text"
            list="location-suggestions"
            placeholder="ZIP, city, or city, state"
            value={usingCurrentLocation ? 'Current location' : locationText}
            onChange={(event) => setLocationText(event.target.value)}
            readOnly={usingCurrentLocation}
            required={!usingCurrentLocation}
            autoComplete="off"
          />
          <datalist id="location-suggestions">
            {locationSuggestions.map((suggestion) => (
              <option key={suggestion.zipCode ?? `${suggestion.city}-${suggestion.stateCode}`} value={suggestion.label} />
            ))}
          </datalist>
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
          }${justFoundLocation ? ' is-found' : ''}`}
          onClick={geolocation.request}
          disabled={geolocation.status === 'requesting'}
        >
          {geolocation.status === 'requesting' ? (
            <span className="spinner spinner-sm" aria-hidden="true" />
          ) : usingCurrentLocation ? (
            <CheckIcon width={15} height={15} />
          ) : (
            <CrosshairIcon width={15} height={15} />
          )}
          {geolocation.status === 'requesting'
            ? 'Finding your location…'
            : justFoundLocation
              ? 'Location found'
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

      <button type="submit" className="primary-button search-cta" disabled={disabled} aria-busy={disabled}>
        {disabled ? <span className="spinner" aria-hidden="true" /> : <SearchIcon width={18} height={18} />}
        {disabled ? 'Searching…' : 'Find providers'}
      </button>
    </form>
  )
}

export default SearchForm
