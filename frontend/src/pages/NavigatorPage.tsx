import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, fetchNavigatorDashboard, fetchReminders, fetchSavedSearches } from '../api/client'
import type { NavigatorDashboardDto, NavigatorProviderDto, ReminderDto, SavedPlanDto, SavedSearchDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import {
  AlertIcon,
  BadgeIcon,
  BookmarkIcon,
  BuildingIcon,
  CompareIcon,
  FolderIcon,
  LocationIcon,
} from '../components/icons'
import NavigationStatusSelect from '../components/NavigationStatusSelect'
import ReminderPanel from '../components/ReminderPanel'
import SavedPlanCard from '../components/SavedPlanCard'
import { buildRunSearchUrl } from './SavedSearchesPage'
import { formattedAddress, initialsFor, providerDisplayName } from '../utils/providerDisplay'
import { evidenceStatusCopy } from '../utils/networkEvidenceDisplay'

type FilterValue = 'all' | 'TO_CONTACT' | 'CONTACTED' | 'needs-verification' | 'checklist-complete'

const FILTERS: { value: FilterValue; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'TO_CONTACT', label: 'To contact' },
  { value: 'CONTACTED', label: 'Contacted' },
  { value: 'needs-verification', label: 'Needs verification' },
  { value: 'checklist-complete', label: 'Checklist complete' },
]

const MAX_COMPARE = 3

function matchesFilter(provider: NavigatorProviderDto, filter: FilterValue): boolean {
  if (filter === 'all') return true
  if (filter === 'needs-verification') return provider.verificationCompleted < provider.verificationTotal
  if (filter === 'checklist-complete') return provider.verificationCompleted >= provider.verificationTotal && provider.verificationTotal > 0
  return provider.status === filter
}

function NavigatorPage() {
  const [dashboard, setDashboard] = useState<NavigatorDashboardDto | null>(null)
  const [reminders, setReminders] = useState<ReminderDto[]>([])
  const [savedSearches, setSavedSearches] = useState<SavedSearchDto[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)
  const [filter, setFilter] = useState<FilterValue>('all')
  const [query, setQuery] = useState('')
  const [selectedForCompare, setSelectedForCompare] = useState<Set<number>>(new Set())

  useEffect(() => {
    let cancelled = false
    Promise.all([fetchNavigatorDashboard(), fetchReminders(), fetchSavedSearches()])
      .then(([dashboardResult, reminderResult, savedSearchResult]) => {
        if (cancelled) return
        setDashboard(dashboardResult)
        setReminders(reminderResult)
        setSavedSearches(savedSearchResult)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setStatus('error')
        setErrorMessage(error instanceof ApiError ? error.message : 'Unable to reach the search service. Please try again.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const filteredProviders = useMemo(() => {
    if (!dashboard) return []
    const trimmedQuery = query.trim().toLowerCase()
    return dashboard.providers.filter((provider) => {
      if (!matchesFilter(provider, filter)) return false
      if (!trimmedQuery) return true
      return providerDisplayName(provider).toLowerCase().includes(trimmedQuery)
    })
  }, [dashboard, filter, query])

  function toggleCompareSelection(providerId: number) {
    setSelectedForCompare((previous) => {
      const next = new Set(previous)
      if (next.has(providerId)) {
        next.delete(providerId)
      } else if (next.size < MAX_COMPARE) {
        next.add(providerId)
      }
      return next
    })
  }

  function handleStatusChange(providerId: number, nextStatus: NavigatorProviderDto['status']) {
    setDashboard((current) => {
      if (!current) return current
      return {
        ...current,
        toContactCount: current.providers.reduce(
          (count, p) => count + ((p.providerId === providerId ? nextStatus : p.status) === 'TO_CONTACT' ? 1 : 0),
          0,
        ),
        providers: current.providers.map((provider) =>
          provider.providerId === providerId ? { ...provider, status: nextStatus } : provider,
        ),
      }
    })
  }

  function handleSavedPlanChanged(plan: SavedPlanDto | null) {
    setDashboard((current) => (current ? { ...current, savedPlan: plan } : current))
  }

  const isEmpty =
    dashboard != null &&
    dashboard.providers.length === 0 &&
    dashboard.shortlists.length === 0 &&
    reminders.length === 0 &&
    dashboard.savedPlan == null

  return (
    <div className="page">
      <Header />
      <main className="container detail-content navigator-page">
        <div>
          <h1>Your care navigator</h1>
          <p className="results-subtext">Keep track of providers you&rsquo;re considering and the details you still want to confirm.</p>
        </div>

        {status === 'loading' && <p className="results-heading-loading">Loading your navigator…</p>}

        {status === 'error' && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={22} height={22} />
            <div className="state-panel-copy">
              <h3>We couldn&rsquo;t load your navigator</h3>
              <p>{errorMessage}</p>
            </div>
          </div>
        )}

        {status === 'success' && dashboard && isEmpty && (
          <div className="state-panel empty-panel">
            <div className="empty-panel-icon">
              <BookmarkIcon width={22} height={22} />
            </div>
            <h3>Your navigator is ready</h3>
            <p className="state-hint">Save a provider to start organizing options.</p>
            <Link className="primary-button" to="/#search-panel">
              Find providers
            </Link>
          </div>
        )}

        {status === 'success' && dashboard && !isEmpty && (
          <>
            <p className="navigator-summary">
              {dashboard.savedCount} provider{dashboard.savedCount === 1 ? '' : 's'} saved &middot; {dashboard.toContactCount} still to
              contact &middot; {Math.max(dashboard.savedCount - dashboard.verificationNeededCount, 0)} verification checklist
              {dashboard.savedCount - dashboard.verificationNeededCount === 1 ? '' : 's'} complete
            </p>

            {dashboard.providers.length > 0 && (
              <section className="navigator-panel">
                <h2>Providers to consider</h2>
                <div className="navigator-filter-row">
                  <div className="navigator-filter-chips" role="group" aria-label="Filter providers">
                    {FILTERS.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        className={`filter-chip${filter === option.value ? ' is-active' : ''}`}
                        aria-pressed={filter === option.value}
                        onClick={() => setFilter(option.value)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                  <input
                    type="search"
                    className="navigator-search-input"
                    placeholder="Search your providers"
                    aria-label="Search your saved providers"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                  />
                </div>

                {selectedForCompare.size >= 2 && (
                  <Link className="secondary-button navigator-compare-link" to={`/compare?ids=${Array.from(selectedForCompare).join(',')}`}>
                    <CompareIcon width={16} height={16} />
                    Compare selected ({selectedForCompare.size})
                  </Link>
                )}

                <ul className="provider-list">
                  {filteredProviders.map((provider) => (
                    <NavigatorProviderCard
                      key={provider.providerId}
                      provider={provider}
                      selected={selectedForCompare.has(provider.providerId)}
                      onToggleCompare={() => toggleCompareSelection(provider.providerId)}
                      onStatusChanged={(nextStatus) => handleStatusChange(provider.providerId, nextStatus)}
                    />
                  ))}
                  {filteredProviders.length === 0 && <p className="results-subtext">No providers match this filter.</p>}
                </ul>
              </section>
            )}

            {dashboard.shortlists.length > 0 && (
              <section className="navigator-panel">
                <h2>
                  <FolderIcon width={18} height={18} />
                  Shortlists
                </h2>
                <ul className="navigator-shortlist-list">
                  {dashboard.shortlists.map((shortlist) => (
                    <li key={shortlist.id}>
                      <Link to={`/shortlists/${shortlist.id}`}>{shortlist.name}</Link>
                      <span className="results-subtext">
                        {shortlist.providerCount} provider{shortlist.providerCount === 1 ? '' : 's'}
                        {shortlist.toContactCount > 0 && ` · ${shortlist.toContactCount} to contact`}
                        {shortlist.contactedCount > 0 && ` · ${shortlist.contactedCount} contacted`}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            <ReminderPanel reminders={reminders} onChanged={setReminders} />

            <SavedPlanCard savedPlan={dashboard.savedPlan} onChanged={handleSavedPlanChanged} />

            {savedSearches.length > 0 && (
              <section className="navigator-panel">
                <h2>
                  <BookmarkIcon width={18} height={18} />
                  Saved searches
                </h2>
                <ul className="navigator-saved-search-list">
                  {savedSearches.map((search) => (
                    <li key={search.id}>
                      <span>
                        {search.name || search.specialtyName} &middot; {search.locationText ?? 'Your location'}
                      </span>
                      <Link className="ghost-button" to={buildRunSearchUrl(search)}>
                        Run
                      </Link>
                    </li>
                  ))}
                </ul>
                <Link className="link-button" to="/saved-searches">
                  Manage saved searches
                </Link>
              </section>
            )}
          </>
        )}
      </main>
      <Footer />
    </div>
  )
}

function NavigatorProviderCard({
  provider,
  selected,
  onToggleCompare,
  onStatusChanged,
}: {
  provider: NavigatorProviderDto
  selected: boolean
  onToggleCompare: () => void
  onStatusChanged: (status: NavigatorProviderDto['status']) => void
}) {
  const name = providerDisplayName(provider)
  const isOrganization = provider.entityType === 'ORGANIZATION'
  const location = provider.location

  return (
    <li className={`provider-card${selected ? ' is-selected' : ''}`}>
      <div className="provider-card-top">
        <label className="compare-checkbox" style={{ marginRight: 4 }}>
          <input type="checkbox" checked={selected} onChange={onToggleCompare} aria-label={`Select ${name} to compare`} />
          <span className="compare-checkbox-box" aria-hidden="true" />
        </label>
        <div className={`avatar${isOrganization ? ' avatar-organization' : ''}`} aria-hidden="true">
          {isOrganization ? <BuildingIcon width={20} height={20} /> : initialsFor(name)}
        </div>
        <div className="provider-card-heading">
          <h3>{name}</h3>
        </div>
      </div>

      <div className="provider-card-details">
        {location && (
          <p className="detail">
            <LocationIcon width={16} height={16} />
            <span>
              {formattedAddress(location).line1}
              <br />
              {formattedAddress(location).line2}
            </span>
          </p>
        )}
        <p className="npi">
          <BadgeIcon width={14} height={14} />
          <span>NPI {provider.npiNumber}</span>
        </p>
      </div>

      <div className="navigator-card-status-row">
        <span className="results-subtext">Status</span>
        <NavigationStatusSelect providerId={provider.providerId} status={provider.status} onChanged={onStatusChanged} />
      </div>

      <p className="results-subtext">
        Verification: {provider.verificationCompleted} of {provider.verificationTotal} reviewed
      </p>
      {provider.networkEvidence && <p className="results-subtext">{evidenceStatusCopy(provider.networkEvidence.status)}</p>}
      <p className="navigator-next-action">Next: {provider.nextAction}</p>

      <div className="provider-card-actions">
        <div className="provider-card-buttons">
          <Link className="ghost-button" to={`/providers/${provider.providerId}`}>
            View provider
          </Link>
        </div>
      </div>
    </li>
  )
}

export default NavigatorPage
