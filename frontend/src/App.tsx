import { useEffect, useState } from 'react'
import './App.css'
import { fetchInsuranceCarriers, fetchSpecialties, searchProviders } from './api/client'
import type { InsuranceCarrierDto, ProviderSearchResultDto, SpecialtyDto } from './api/types'
import Header from './components/Header'
import Hero from './components/Hero'
import { InfoIcon } from './components/icons'
import SearchForm, { type SearchFormValues } from './components/SearchForm'
import ProviderResults, { type SearchStatus } from './components/ProviderResults'

const SEARCH_RADIUS_MILES = 25

function App() {
  const [specialties, setSpecialties] = useState<SpecialtyDto[]>([])
  const [insuranceCarriers, setInsuranceCarriers] = useState<InsuranceCarrierDto[]>([])
  const [referenceDataError, setReferenceDataError] = useState<string | null>(null)

  const [status, setStatus] = useState<SearchStatus>('idle')
  const [results, setResults] = useState<ProviderSearchResultDto[]>([])
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)
  const [lastSearch, setLastSearch] = useState<SearchFormValues | null>(null)

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

  async function runSearch(values: SearchFormValues) {
    setLastSearch(values)
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

  function handleRetry() {
    if (lastSearch) {
      void runSearch(lastSearch)
    }
  }

  const selectedCarrierName = insuranceCarriers.find(
    (carrier) => String(carrier.id) === lastSearch?.insuranceCarrierId,
  )?.name
  const selectedSpecialtyName = specialties.find((s) => s.code === lastSearch?.specialty)?.name

  return (
    <div className="page" id="top">
      <Header />
      <Hero />

      <main className="container main-content">
        {referenceDataError && (
          <div className="state-panel error-panel" role="alert">
            <InfoIcon width={20} height={20} />
            <div className="state-panel-copy">
              <p>{referenceDataError}</p>
            </div>
          </div>
        )}

        <section className="search-section" id="search-panel">
          <SearchForm
            specialties={specialties}
            insuranceCarriers={insuranceCarriers}
            onSearch={runSearch}
            disabled={status === 'loading'}
          />

          <div className="disclaimer-panel">
            <InfoIcon width={18} height={18} />
            <p>
              Insurance selection is for demonstration only. Coverage is not verified. Confirm
              directly with the provider or insurer.
            </p>
          </div>

          {status === 'success' && selectedCarrierName && (
            <p className="insurance-note">
              Insurance filter (&ldquo;{selectedCarrierName}&rdquo;) is informational only and does
              not affect these results.
            </p>
          )}
        </section>

        <ProviderResults
          status={status}
          results={results}
          errorMessage={errorMessage}
          specialtyName={selectedSpecialtyName}
          zip={lastSearch?.zip}
          radiusMiles={SEARCH_RADIUS_MILES}
          onRetry={handleRetry}
        />
      </main>
    </div>
  )
}

export default App
