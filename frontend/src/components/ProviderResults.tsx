import { lazy, Suspense, useState } from 'react'
import type { PracticalFitFilters } from '../api/client'
import type { ProviderSearchResultDto, SortOption } from '../api/types'
import PracticalFitFilterBar from './PracticalFitFilterBar'
import ProviderCard from './ProviderCard'
import ProviderCardSkeleton from './ProviderCardSkeleton'
import { AlertIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, CloseIcon, ListIcon, LocationIcon, MapIcon } from './icons'

// Lazy-loaded: users who never open the map shouldn't pay for Leaflet in the initial bundle
// (CLAUDE.md "Map Code Splitting").
const ResultsMap = lazy(() => import('./ResultsMap'))

export type SearchStatus = 'idle' | 'loading' | 'error' | 'success'
type ResultsView = 'list' | 'map'

interface ProviderResultsProps {
  status: SearchStatus
  results: ProviderSearchResultDto[]
  errorMessage?: string
  specialtyName?: string
  originLabel?: string | null
  planId?: number
  radiusMiles?: number
  totalElements?: number
  page?: number
  totalPages?: number
  sort?: SortOption
  onSortChange?: (sort: SortOption) => void
  onPageChange?: (page: number) => void
  onRetry?: () => void
  onClearSearch?: () => void
  onShare?: () => void
  shareCopied?: boolean
  onSaveSearch?: () => void
  isSavingSearch?: boolean
  searchSaved?: boolean
  filters?: PracticalFitFilters
  onFiltersChange?: (filters: PracticalFitFilters) => void
}

function ProviderResults({
  status,
  results,
  errorMessage,
  specialtyName,
  originLabel,
  planId,
  radiusMiles = 25,
  totalElements,
  page = 0,
  totalPages = 0,
  sort = 'distance',
  onSortChange,
  onPageChange,
  onRetry,
  onClearSearch,
  onShare,
  shareCopied = false,
  onSaveSearch,
  isSavingSearch = false,
  searchSaved = false,
  filters,
  onFiltersChange,
}: ProviderResultsProps) {
  const [view, setView] = useState<ResultsView>('list')
  const [selectedProviderId, setSelectedProviderId] = useState<number | null>(null)

  if (status === 'idle') {
    return null
  }

  if (status === 'error') {
    return (
      <section className="results-section" aria-live="polite">
        <div className="state-panel error-panel" role="alert">
          <AlertIcon width={22} height={22} />
          <div className="state-panel-copy">
            <h3>We couldn&rsquo;t load provider results</h3>
            <p>{errorMessage ?? 'Unable to reach the search service. Please try again.'}</p>
          </div>
          {onRetry && (
            <button type="button" className="secondary-button" onClick={onRetry}>
              Retry search
            </button>
          )}
        </div>
      </section>
    )
  }

  if (status === 'loading') {
    return (
      <section className="results-section" aria-live="polite" aria-busy="true">
        <p className="results-heading-loading">Searching for providers…</p>
        <ul className="provider-list">
          <ProviderCardSkeleton />
          <ProviderCardSkeleton />
          <ProviderCardSkeleton />
        </ul>
      </section>
    )
  }

  const headingLabel = originLabel ?? 'your location'
  const effectiveTotal = totalElements ?? results.length

  return (
    <section className="results-section" aria-live="polite">
      <div className="results-toolbar">
        <div className="results-heading">
          <h2>Providers near {headingLabel}</h2>
          {results.length > 0 && (
            <p className="results-subtext">
              {effectiveTotal} provider{effectiveTotal === 1 ? '' : 's'} within {radiusMiles} miles of{' '}
              {headingLabel}
            </p>
          )}
        </div>

        {results.length > 0 && (
          <div className="results-actions">
            <label className="sort-control">
              <span>Sort</span>
              <select
                className="plain-select"
                value={sort}
                onChange={(event) => onSortChange?.(event.target.value as SortOption)}
              >
                <option value="distance">Nearest</option>
                <option value="name">Name A–Z</option>
                <option value="name-desc">Name Z–A</option>
              </select>
            </label>

            {onShare && (
              <button
                type="button"
                className={`secondary-button share-button${shareCopied ? ' is-success' : ''}`}
                onClick={onShare}
                aria-live="polite"
              >
                {shareCopied ? (
                  <>
                    <CheckIcon width={14} height={14} />
                    Link copied
                  </>
                ) : (
                  'Share search'
                )}
              </button>
            )}

            {onSaveSearch && (
              <button
                type="button"
                className={`secondary-button${searchSaved ? ' is-success' : ''}`}
                onClick={onSaveSearch}
                disabled={isSavingSearch || searchSaved}
                aria-live="polite"
              >
                {searchSaved ? (
                  <>
                    <CheckIcon width={14} height={14} />
                    Search saved
                  </>
                ) : (
                  'Save this search'
                )}
              </button>
            )}
          </div>
        )}
      </div>

      <div className="filter-chips">
        {specialtyName && <span className="chip">{specialtyName}</span>}
        {originLabel && <span className="chip">{originLabel}</span>}
        <span className="chip">{radiusMiles} miles</span>
        {onClearSearch && (
          <button type="button" className="chip chip-clear" onClick={onClearSearch}>
            <CloseIcon width={12} height={12} />
            Clear search
          </button>
        )}
      </div>

      {filters && onFiltersChange && results.length > 0 && (
        <PracticalFitFilterBar filters={filters} onChange={onFiltersChange} networkEvidenceAvailable={planId != null} />
      )}

      {results.length === 0 ? (
        <div className="state-panel empty-panel">
          <div className="empty-panel-icon" aria-hidden="true">
            <LocationIcon width={22} height={22} />
          </div>
          <h3>No providers found nearby</h3>
          <p>
            We couldn&rsquo;t find any matching providers within {radiusMiles} miles of {headingLabel}.
          </p>
          <ul className="zero-result-recovery">
            <li>Increase your search radius</li>
            {filters && Object.keys(filters).length > 0 && onFiltersChange && (
              <li>
                <button type="button" className="link-button" onClick={() => onFiltersChange({})}>
                  Clear your practical filters
                </button>
              </li>
            )}
            <li>Try a nearby supported ZIP code, or double-check the spelling of your location</li>
            <li>Search for a provider by name instead, using the box above</li>
          </ul>
          <p className="state-hint">
            This demo dataset currently covers a limited Long Beach / Los Angeles area -- try a
            nearby ZIP such as 90802, 90803, 90806, 90815, 90712, or 90755.
          </p>
        </div>
      ) : (
        <>
          <div className="results-view-toggle">
            <button
              type="button"
              className={`view-toggle-button${view === 'list' ? ' is-active' : ''}`}
              aria-pressed={view === 'list'}
              onClick={() => setView('list')}
            >
              <ListIcon width={15} height={15} />
              List
            </button>
            <button
              type="button"
              className={`view-toggle-button${view === 'map' ? ' is-active' : ''}`}
              aria-pressed={view === 'map'}
              onClick={() => setView('map')}
            >
              <MapIcon width={15} height={15} />
              Map
            </button>
          </div>

          <a href="#results-list-pane" className="skip-map-link">
            Skip map
          </a>

          <div className="results-layout">
            <div className="results-list-pane" id="results-list-pane" data-mobile-hidden={view === 'map'}>
              <ul className="provider-list">
                {results.map((provider, index) => (
                  <ProviderCard
                    key={provider.id}
                    provider={provider}
                    entranceIndex={index}
                    originLabel={originLabel}
                    planId={planId}
                    onSelect={() => setSelectedProviderId(provider.id)}
                    isSelected={provider.id === selectedProviderId}
                  />
                ))}
              </ul>

              {totalPages > 1 && (
                <nav className="pagination" aria-label="Search results pages">
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => onPageChange?.(page - 1)}
                    disabled={page <= 0}
                  >
                    <ChevronLeftIcon width={14} height={14} />
                    Previous
                  </button>
                  <span className="pagination-status">
                    Page {page + 1} of {totalPages}
                  </span>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => onPageChange?.(page + 1)}
                    disabled={page >= totalPages - 1}
                  >
                    Next
                    <ChevronRightIcon width={14} height={14} />
                  </button>
                </nav>
              )}
            </div>

            <div className="results-map-pane" data-mobile-hidden={view === 'list'}>
              <Suspense fallback={<div className="map-loading-skeleton" aria-hidden="true" />}>
                <ResultsMap
                  results={results}
                  selectedProviderId={selectedProviderId}
                  onSelectProvider={setSelectedProviderId}
                  originLabel={originLabel}
                />
              </Suspense>
              <p className="map-precision-note">
                Some map locations are approximate because the public provider record does not
                include precise geocoded coordinates.
              </p>
            </div>
          </div>
        </>
      )}
    </section>
  )
}

export default ProviderResults
