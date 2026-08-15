import { useEffect, useState } from 'react'
import './App.css'
import { fetchInsuranceCarriers, fetchSpecialties, searchProviders } from './api/client'
import type { InsuranceCarrierDto, ProviderSearchResultDto, SpecialtyDto } from './api/types'
import SearchForm, { type SearchFormValues } from './components/SearchForm'
import ProviderResults, { type SearchStatus } from './components/ProviderResults'

function App() {
  const [specialties, setSpecialties] = useState<SpecialtyDto[]>([])
  const [insuranceCarriers, setInsuranceCarriers] = useState<InsuranceCarrierDto[]>([])
  const [referenceDataError, setReferenceDataError] = useState<string | null>(null)

  const [status, setStatus] = useState<SearchStatus>('idle')
  const [results, setResults] = useState<ProviderSearchResultDto[]>([])
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)
  const [selectedInsuranceId, setSelectedInsuranceId] = useState('')

  useEffect(() => {
    let cancelled = false

    Promise.all([fetchSpecialties(), fetchInsuranceCarriers()])
      .then(([specialtyList, carrierList]) => {
        if (cancelled) return
        setSpecialties(specialtyList)
        setInsuranceCarriers(carrierList)
      })
      .catch(() => {
        if (cancelled) return
        setReferenceDataError('Unable to load specialties and insurance carriers. Is the API running?')
      })

    return () => {
      cancelled = true
    }
  }, [])

  async function handleSearch(values: SearchFormValues) {
    setSelectedInsuranceId(values.insuranceCarrierId)
    setStatus('loading')
    setErrorMessage(undefined)
    try {
      const response = await searchProviders({ specialty: values.specialty, zip: values.zip })
      setResults(response.results)
      setStatus('success')
    } catch (error) {
      setResults([])
      setStatus('error')
      setErrorMessage(error instanceof Error ? error.message : 'Search failed.')
    }
  }

  const selectedCarrierName = insuranceCarriers.find(
    (carrier) => String(carrier.id) === selectedInsuranceId,
  )?.name

  return (
    <div className="page">
      <header className="page-header">
        <h1>DocFit AI</h1>
        <p className="tagline">Find healthcare providers that fit your needs.</p>
      </header>

      <main>
        {referenceDataError && (
          <p className="status-message error" role="alert">
            {referenceDataError}
          </p>
        )}

        <SearchForm
          specialties={specialties}
          insuranceCarriers={insuranceCarriers}
          onSearch={handleSearch}
          disabled={status === 'loading'}
        />

        <p className="disclaimer">
          Insurance information is for demo purposes only. Confirm coverage directly with the
          provider or insurer.
        </p>

        {status === 'success' && selectedCarrierName && (
          <p className="insurance-note">
            Insurance filter (&ldquo;{selectedCarrierName}&rdquo;) is informational only and does
            not affect these results.
          </p>
        )}

        <ProviderResults status={status} results={results} errorMessage={errorMessage} />
      </main>
    </div>
  )
}

export default App
