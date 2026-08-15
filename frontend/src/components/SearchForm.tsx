import { useState, type FormEvent } from 'react'
import type { InsuranceCarrierDto, SpecialtyDto } from '../api/types'
import { InsuranceIcon, LocationIcon, SearchIcon, SpecialtyIcon } from './icons'

export interface SearchFormValues {
  specialty: string
  zip: string
  insuranceCarrierId: string
}

interface SearchFormProps {
  specialties: SpecialtyDto[]
  insuranceCarriers: InsuranceCarrierDto[]
  onSearch: (values: SearchFormValues) => void
  disabled?: boolean
}

function SearchForm({ specialties, insuranceCarriers, onSearch, disabled = false }: SearchFormProps) {
  const [specialty, setSpecialty] = useState('')
  const [insuranceCarrierId, setInsuranceCarrierId] = useState('')
  const [zip, setZip] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSearch({ specialty, zip, insuranceCarrierId })
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

      <div className="field">
        <label htmlFor="zip">ZIP code</label>
        <div className="input-with-icon">
          <LocationIcon width={18} height={18} />
          <input
            id="zip"
            name="zip"
            type="text"
            inputMode="numeric"
            pattern="\d{5}"
            maxLength={5}
            placeholder="90802"
            value={zip}
            onChange={(event) => setZip(event.target.value)}
            required
          />
        </div>
      </div>

      <button type="submit" className="primary-button search-cta" disabled={disabled}>
        <SearchIcon width={18} height={18} />
        {disabled ? 'Searching…' : 'Find providers'}
      </button>
    </form>
  )
}

export default SearchForm
