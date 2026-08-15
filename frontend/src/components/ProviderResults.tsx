import type { ProviderSearchResultDto, SortOption } from '../api/types'
import ProviderCard from './ProviderCard'
import ProviderCardSkeleton from './ProviderCardSkeleton'
import { AlertIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, CloseIcon, LocationIcon } from './icons'

export type SearchStatus = 'idle' | 'loading' | 'error' | 'success'

interface ProviderResultsProps {
  status: SearchStatus
  results: ProviderSearchResultDto[]
  errorMessage?: string
  specialtyName?: string
  originLabel?: string | null
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
}

function ProviderResults({
  status,
  results,
  errorMessage,
  specialtyName,
  originLabel,
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
}: ProviderResultsProps) {
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

      {results.length === 0 ? (
        <div className="state-panel empty-panel">
          <div className="empty-panel-icon" aria-hidden="true">
            <LocationIcon width={22} height={22} />
          </div>
          <h3>No providers found nearby</h3>
          <p>
            We couldn&rsquo;t find any matching providers within {radiusMiles} miles of {headingLabel}.
          </p>
          <p className="state-hint">
            Try increasing your search radius or choosing a nearby location. This demo dataset
            currently covers a limited Long Beach / Los Angeles area -- try a nearby ZIP such as
            90802, 90803, 90806, 90815, 90712, or 90755.
          </p>
        </div>
      ) : (
        <>
          <ul className="provider-list">
            {results.map((provider, index) => (
              <ProviderCard key={provider.id} provider={provider} entranceIndex={index} originLabel={originLabel} />
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
        </>
      )}
    </section>
  )
}

export default ProviderResults
