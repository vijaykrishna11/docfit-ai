import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, fetchPayers, fetchSpecialties, saveSearch, searchProviders, type PracticalFitFilters } from '../api/client'
import type { PayerDto, ProviderSearchResultDto, SortOption, SpecialtyDto } from '../api/types'
import About from '../components/About'
import CompareBar from '../components/CompareBar'
import DataSources from '../components/DataSources'
import Footer from '../components/Footer'
import Header from '../components/Header'
import Hero from '../components/Hero'
import HowItWorks from '../components/HowItWorks'
import { InfoIcon } from '../components/icons'
import PopularSpecialties from '../components/PopularSpecialties'
import PrivacyAccountMessage from '../components/PrivacyAccountMessage'
import ProviderNameSearch from '../components/ProviderNameSearch'
import ProviderResults, { type SearchStatus } from '../components/ProviderResults'
import RecentlyViewed from '../components/RecentlyViewed'
import SearchForm, { type SearchFormValues } from '../components/SearchForm'
import SupportedAreas from '../components/SupportedAreas'
import WhyDocFit from '../components/WhyDocFit'
import { useAuth } from '../context/AuthContext'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'
const DEFAULT_RADIUS = 25
const DEFAULT_SORT: SortOption = 'distance'

function HomePage() {
  const { isAuthenticated } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()

  const [specialties, setSpecialties] = useState<SpecialtyDto[]>([])
  const [payers, setPayers] = useState<PayerDto[]>([])
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
    Promise.all([fetchSpecialties(), fetchPayers()])
      .then(([specialtyList, payerList]) => {
        if (cancelled) return
        setSpecialties(specialtyList)
        setPayers(payerList)
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
  const payerId = searchParams.get('payerId') ?? ''
  const planId = searchParams.get('planId') ?? ''

  const filters: PracticalFitFilters = useMemo(() => {
    const providerType = searchParams.get('providerType')
    const next: PracticalFitFilters = {}
    if (providerType === 'INDIVIDUAL' || providerType === 'ORGANIZATION') {
      next.providerType = providerType
    }
    if (searchParams.get('hasPhone') === 'true') next.hasPhone = true
    if (searchParams.get('preciseLocationOnly') === 'true') next.preciseLocationOnly = true
    if (searchParams.get('networkEvidenceFound') === 'true') next.networkEvidenceFound = true
    if (searchParams.get('multipleLocations') === 'true') next.multipleLocations = true
    return next
  }, [searchParams])

  const hasSearch = Boolean(specialty) && Boolean(location || (lat && lng))

  // Returns a cancel function (same pattern as loadReferenceData above): if search params change
  // again before this request resolves, the effect's cleanup marks it cancelled so an
  // out-of-order/slow response can't overwrite the results of a newer search that already
  // completed.
  function runSearch(): () => void {
    let cancelled = false
    setStatus('loading')
    setErrorMessage(undefined)
    searchProviders({
      specialty,
      radius,
      sort,
      page,
      location: lat && lng ? undefined : location || undefined,
      lat: lat ? Number(lat) : undefined,
      lng: lng ? Number(lng) : undefined,
      planId: planId ? Number(planId) : undefined,
      ...filters,
    })
      .then((response) => {
        if (cancelled) return
        setResults(response.results)
        setOriginLabel(response.originLabel)
        setTotalElements(response.totalElements)
        setTotalPages(response.totalPages)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setResults([])
        setOriginLabel(null)
        setTotalElements(0)
        setTotalPages(0)
        setStatus('error')
        setErrorMessage(error instanceof ApiError ? error.message : UNREACHABLE_MESSAGE)
      })
    return () => {
      cancelled = true
    }
  }

  useEffect(() => {
    let cleanup: (() => void) | undefined
    if (hasSearch) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch-on-param-change pattern
      cleanup = runSearch()
    } else {
      setStatus('idle')
      setResults([])
    }
    setSearchSaved(false)
    return cleanup
    // Search re-runs only when the URL-derived search criteria actually change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [specialty, location, lat, lng, radius, sort, page, planId, filters])

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

  function scrollToSearchPanel() {
    window.setTimeout(() => {
      document.getElementById('search-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 50)
  }

  function handleSpecialtyShortcut(code: string) {
    const next = new URLSearchParams(searchParams)
    next.set('specialty', code)
    setSearchParams(next)
    scrollToSearchPanel()
    window.setTimeout(() => document.getElementById('location')?.focus(), 350)
  }

  function handleAreaShortcut(zipCode: string) {
    const next = new URLSearchParams(searchParams)
    next.set('location', zipCode)
    next.delete('lat')
    next.delete('lng')
    setSearchParams(next)
    scrollToSearchPanel()
  }

  function handleSearch(values: SearchFormValues) {
    const next = new URLSearchParams()
    next.set('specialty', values.specialty)
    next.set('radius', String(values.radius))
    next.set('sort', DEFAULT_SORT)
    next.set('page', '0')
    if (values.payerId) {
      next.set('payerId', values.payerId)
    }
    if (values.planId) {
      next.set('planId', values.planId)
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

  function handleFiltersChange(nextFilters: PracticalFitFilters) {
    const next = new URLSearchParams(searchParams)
    next.delete('providerType')
    next.delete('hasPhone')
    next.delete('preciseLocationOnly')
    next.delete('networkEvidenceFound')
    next.delete('multipleLocations')
    if (nextFilters.providerType) next.set('providerType', nextFilters.providerType)
    if (nextFilters.hasPhone) next.set('hasPhone', 'true')
    if (nextFilters.preciseLocationOnly) next.set('preciseLocationOnly', 'true')
    if (nextFilters.networkEvidenceFound) next.set('networkEvidenceFound', 'true')
    if (nextFilters.multipleLocations) next.set('multipleLocations', 'true')
    next.set('page', '0')
    setSearchParams(next)
  }

  function handleClearSearch() {
    setSearchParams(new URLSearchParams())
    setStatus('idle')
    setResults([])
    setOriginLabel(null)
  }

  function handleRetry() {
    runSearch()
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

  const selectedPayer = payers.find((payer) => String(payer.id) === payerId)
  const selectedSpecialtyName = specialties.find((s) => s.code === specialty)?.name

  const formInitialValues = useMemo(
    () => ({
      specialty,
      payerId,
      planId,
      location: lat && lng ? '' : location,
      radius: radius || DEFAULT_RADIUS,
    }),
    [specialty, payerId, planId, location, lat, lng, radius],
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
            key={`${specialty}|${payerId}|${planId}|${location}|${lat}|${lng}|${radius}`}
            specialties={specialties}
            payers={payers}
            onSearch={handleSearch}
            disabled={status === 'loading'}
            initialValues={formInitialValues}
          />

          <ProviderNameSearch />

          <div className="disclaimer-panel">
            <InfoIcon width={18} height={18} />
            <p>
              {selectedPayer?.hasIntegratedPlans
                ? 'Network directory information can change and does not guarantee coverage or payment. Confirm eligibility and benefits directly with your insurer.'
                : 'Insurance coverage is not verified for most insurers in this demo. Confirm directly with the provider or insurer.'}
            </p>
          </div>

          {status === 'success' && selectedPayer && !selectedPayer.hasIntegratedPlans && (
            <p className="insurance-note">
              Network verification is not currently available for {selectedPayer.name}. These
              results are not filtered by insurance.
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
          planId={planId ? Number(planId) : undefined}
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
          filters={filters}
          onFiltersChange={handleFiltersChange}
        />
      </main>

      <CompareBar />

      <PopularSpecialties specialties={specialties} onSelect={handleSpecialtyShortcut} />
      <HowItWorks />
      <SupportedAreas onSelect={handleAreaShortcut} />
      <DataSources />
      <WhyDocFit />
      <About />
      <PrivacyAccountMessage />
      <Footer />
    </div>
  )
}

export default HomePage
