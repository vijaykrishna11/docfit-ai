import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, fetchInsuranceCarriers, fetchSpecialties, saveSearch, searchProviders } from '../api/client'
import type { InsuranceCarrierDto, ProviderSearchResultDto, SortOption, SpecialtyDto } from '../api/types'
import About from '../components/About'
import CompareBar from '../components/CompareBar'
import DataSources from '../components/DataSources'
import Footer from '../components/Footer'
import Header from '../components/Header'
import Hero from '../components/Hero'
import HowItWorks from '../components/HowItWorks'
import { InfoIcon } from '../components/icons'
import ProviderNameSearch from '../components/ProviderNameSearch'
import ProviderResults, { type SearchStatus } from '../components/ProviderResults'
import RecentlyViewed from '../components/RecentlyViewed'
import SearchForm, { type SearchFormValues } from '../components/SearchForm'
import { useAuth } from '../context/AuthContext'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'
const DEFAULT_RADIUS = 25
const DEFAULT_SORT: SortOption = 'distance'

function HomePage() {
  const { isAuthenticated } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()

  const [specialties, setSpecialties] = useState<SpecialtyDto[]>([])
  const [insuranceCarriers, setInsuranceCarriers] = useState<InsuranceCarrierDto[]>([])
  const [referenceDataError, setReferenceDataError] = useState<string | null>(null)
  const [referenceDataLoading, setReferenceDataLoading] = useState(true)

  const [status, setStatus] = useState<SearchStatus>('idle')
  const [results, setResults] = useState<ProviderSearchResultDto[]>([])
  const [originLabel, setOriginLabel] = useState<string | null>(null)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)
  const [shareCopied, setShareCopied] = useState(false)
  const [isSavingSearch, setIsSavingSearch] = useState(false)
  const [searchSaved, setSearchSaved] = useState(false)

  function loadReferenceData(): () => void {
    let cancelled = false
    setReferenceDataLoading(true)
    setReferenceDataError(null)
    Promise.all([fetchSpecialties(), fetchInsuranceCarriers()])
      .then(([specialtyList, carrierList]) => {
        if (cancelled) return
        setSpecialties(specialtyList)
        setInsuranceCarriers(carrierList)
      })
      .catch((error: unknown) => {
        if (cancelled) return
        // status === null means the request never reached a server (network/DNS failure,
        // or the backend process isn't running at all) -- a different failure mode from the
        // backend responding with an actual error, so the two get distinct, honest copy.
        const unreachable = error instanceof ApiError && error.status === null
        setReferenceDataError(
          unreachable
            ? "We can't reach the DocFit AI server right now. It may be starting up or temporarily offline."
            : 'Specialties and insurance options failed to load. This is a temporary issue on our end.',
        )
      })
      .finally(() => {
        if (!cancelled) setReferenceDataLoading(false)
      })
    return () => {
      cancelled = true
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch-on-mount pattern; retries are user-triggered via handleRetryReferenceData
    const cancel = loadReferenceData()
    return cancel
  }, [])

  function handleRetryReferenceData() {
    loadReferenceData()
  }

  const specialty = searchParams.get('specialty') ?? ''
  const location = searchParams.get('location') ?? ''
  const lat = searchParams.get('lat')
  const lng = searchParams.get('lng')
  const radius = Number(searchParams.get('radius') ?? DEFAULT_RADIUS)
  const sort = (searchParams.get('sort') as SortOption | null) ?? DEFAULT_SORT
  const page = Number(searchParams.get('page') ?? 0)
  const insuranceCarrierId = searchParams.get('insurance') ?? ''

  const hasSearch = Boolean(specialty) && Boolean(location || (lat && lng))

  async function runSearch() {
    setStatus('loading')
    setErrorMessage(undefined)
    try {
      const response = await searchProviders({
        specialty,
        radius,
        sort,
        page,
        location: lat && lng ? undefined : location || undefined,
        lat: lat ? Number(lat) : undefined,
        lng: lng ? Number(lng) : undefined,
      })
      setResults(response.results)
      setOriginLabel(response.originLabel)
      setTotalElements(response.totalElements)
      setTotalPages(response.totalPages)
      setStatus('success')
    } catch (error) {
      setResults([])
      setOriginLabel(null)
      setTotalElements(0)
      setTotalPages(0)
      setStatus('error')
      setErrorMessage(error instanceof ApiError ? error.message : UNREACHABLE_MESSAGE)
    }
  }

  useEffect(() => {
    if (hasSearch) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch-on-param-change pattern
      void runSearch()
    } else {
      setStatus('idle')
      setResults([])
    }
    setSearchSaved(false)
    // Search re-runs only when the URL-derived search criteria actually change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [specialty, location, lat, lng, radius, sort, page])

  async function handleSaveSearch() {
    setIsSavingSearch(true)
    try {
      await saveSearch({
        specialtyCode: specialty,
        locationText: lat && lng ? undefined : location || undefined,
        latitude: lat ? Number(lat) : undefined,
        longitude: lng ? Number(lng) : undefined,
        radius: radius || DEFAULT_RADIUS,
        sort,
      })
      setSearchSaved(true)
    } catch {
      // Non-fatal: the toolbar simply stays in its unsaved state if the request failed.
    } finally {
      setIsSavingSearch(false)
    }
  }

  function handleSearch(values: SearchFormValues) {
    const next = new URLSearchParams()
    next.set('specialty', values.specialty)
    next.set('radius', String(values.radius))
    next.set('sort', DEFAULT_SORT)
    next.set('page', '0')
    if (values.insuranceCarrierId) {
      next.set('insurance', values.insuranceCarrierId)
    }
    if (values.lat != null && values.lng != null) {
      next.set('lat', String(values.lat))
      next.set('lng', String(values.lng))
    } else if (values.location) {
      next.set('location', values.location)
    }
    setSearchParams(next)
  }

  function handleSortChange(nextSort: SortOption) {
    const next = new URLSearchParams(searchParams)
    next.set('sort', nextSort)
    next.set('page', '0')
    setSearchParams(next)
  }

  function handlePageChange(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(Math.max(0, nextPage)))
    setSearchParams(next)
  }

  function handleClearSearch() {
    setSearchParams(new URLSearchParams())
    setStatus('idle')
    setResults([])
    setOriginLabel(null)
  }

  function handleRetry() {
    void runSearch()
  }

  async function handleShare() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setShareCopied(true)
      window.setTimeout(() => setShareCopied(false), 2500)
    } catch {
      // Clipboard access can be denied by the browser -- fail quietly rather than show a raw error.
    }
  }

  const selectedCarrierName = insuranceCarriers.find((carrier) => String(carrier.id) === insuranceCarrierId)?.name
  const selectedSpecialtyName = specialties.find((s) => s.code === specialty)?.name

  const formInitialValues = useMemo(
    () => ({
      specialty,
      insuranceCarrierId,
      location: lat && lng ? '' : location,
      radius: radius || DEFAULT_RADIUS,
    }),
    [specialty, insuranceCarrierId, location, lat, lng, radius],
  )

  return (
    <div className="page" id="top">
      <Header />
      <Hero />

      <main className="container main-content">
        {referenceDataError && (
          <div className="inline-notice" role="alert">
            <InfoIcon width={16} height={16} />
            <p>{referenceDataError}</p>
            <button
              type="button"
              className="inline-notice-retry"
              onClick={handleRetryReferenceData}
              disabled={referenceDataLoading}
            >
              {referenceDataLoading ? 'Retrying…' : 'Retry'}
            </button>
          </div>
        )}

        <section className="search-section" id="search-panel">
          <SearchForm
            key={`${specialty}|${insuranceCarrierId}|${location}|${lat}|${lng}|${radius}`}
            specialties={specialties}
            insuranceCarriers={insuranceCarriers}
            onSearch={handleSearch}
            disabled={status === 'loading'}
            initialValues={formInitialValues}
          />

          <ProviderNameSearch />

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

          {!hasSearch && <RecentlyViewed />}
        </section>

        <ProviderResults
          status={status}
          results={results}
          errorMessage={errorMessage}
          specialtyName={selectedSpecialtyName}
          originLabel={originLabel}
          radiusMiles={radius || DEFAULT_RADIUS}
          totalElements={totalElements}
          page={page}
          totalPages={totalPages}
          sort={sort}
          onSortChange={handleSortChange}
          onPageChange={handlePageChange}
          onRetry={handleRetry}
          onClearSearch={hasSearch ? handleClearSearch : undefined}
          onShare={status === 'success' ? handleShare : undefined}
          shareCopied={shareCopied}
          onSaveSearch={status === 'success' && isAuthenticated ? handleSaveSearch : undefined}
          isSavingSearch={isSavingSearch}
          searchSaved={searchSaved}
        />
      </main>

      <CompareBar />

      <HowItWorks />
      <DataSources />
      <About />
      <Footer />
    </div>
  )
}

export default HomePage
