import { useState, type FormEvent } from 'react'
import type { InsuranceCarrierDto, SpecialtyDto } from '../api/types'

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
    <form className="search-form" onSubmit={handleSubmit}>
      <div className="field">
        <label htmlFor="specialty">Specialty</label>
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

      <div className="field">
        <label htmlFor="insurance">Insurance (optional)</label>
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

      <div className="field">
        <label htmlFor="zip">ZIP code</label>
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

      <button type="submit" className="search-button" disabled={disabled}>
        {disabled ? 'Searching…' : 'Search'}
      </button>
    </form>
  )
}

export default SearchForm
