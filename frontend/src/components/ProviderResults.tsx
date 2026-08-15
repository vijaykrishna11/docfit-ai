import type { ProviderSearchResultDto } from '../api/types'
import ProviderCard from './ProviderCard'

export type SearchStatus = 'idle' | 'loading' | 'error' | 'success'

interface ProviderResultsProps {
  status: SearchStatus
  results: ProviderSearchResultDto[]
  errorMessage?: string
}

function ProviderResults({ status, results, errorMessage }: ProviderResultsProps) {
  if (status === 'idle') {
    return null
  }

  if (status === 'loading') {
    return (
      <p className="status-message" role="status">
        Searching for providers…
      </p>
    )
  }

  if (status === 'error') {
    return (
      <p className="status-message error" role="alert">
        {errorMessage ?? 'Something went wrong. Please try again.'}
      </p>
    )
  }

  if (results.length === 0) {
    return (
      <p className="status-message" role="status">
        No providers found for that specialty and ZIP code. Try a larger radius or a different
        specialty.
      </p>
    )
  }

  return (
    <ul className="provider-list">
      {results.map((provider) => (
        <ProviderCard key={provider.id} provider={provider} />
      ))}
    </ul>
  )
}

export default ProviderResults
