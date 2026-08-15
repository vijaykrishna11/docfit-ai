import type { ProviderSearchResultDto } from '../api/types'
import ProviderCard from './ProviderCard'
import ProviderCardSkeleton from './ProviderCardSkeleton'
import { AlertIcon } from './icons'

export type SearchStatus = 'idle' | 'loading' | 'error' | 'success'

interface ProviderResultsProps {
  status: SearchStatus
  results: ProviderSearchResultDto[]
  errorMessage?: string
  specialtyName?: string
  originLabel?: string | null
  radiusMiles?: number
  onRetry?: () => void
}

function ProviderResults({
  status,
  results,
  errorMessage,
  specialtyName,
  originLabel,
  radiusMiles = 25,
  onRetry,
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

  return (
    <section className="results-section" aria-live="polite">
      <div className="results-heading">
        <h2>Providers near {headingLabel}</h2>
        {results.length > 0 && (
          <p className="results-subtext">
            Showing {specialtyName ? `${specialtyName.toLowerCase()} ` : ''}providers within{' '}
            {radiusMiles} miles
          </p>
        )}
      </div>

      {results.length === 0 ? (
        <div className="state-panel empty-panel">
          <h3>No providers found nearby</h3>
          <p>
            We couldn&rsquo;t find any matching providers within {radiusMiles} miles of {headingLabel}.
          </p>
          <p className="state-hint">
            This demo dataset currently covers a limited Long Beach / Los Angeles area. Try a
            nearby ZIP such as 90802, 90803, 90806, 90815, 90712, or 90755.
          </p>
        </div>
      ) : (
        <ul className="provider-list">
          {results.map((provider) => (
            <ProviderCard key={provider.id} provider={provider} />
          ))}
        </ul>
      )}
    </section>
  )
}

export default ProviderResults
