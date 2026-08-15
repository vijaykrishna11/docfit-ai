import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchProvidersByName } from '../api/client'
import type { ProviderNameSearchResultDto } from '../api/types'
import { providerDisplayName } from '../utils/providerDisplay'
import { SearchIcon } from './icons'

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 250

function ProviderNameSearch() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<ProviderNameSearchResultDto[]>([])
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < MIN_QUERY_LENGTH) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- clear stale results as the query shrinks below the search threshold
      setResults([])
      setOpen(false)
      return
    }
    let cancelled = false
    const timeout = window.setTimeout(() => {
      searchProvidersByName(trimmed)
        .then((matches) => {
          if (!cancelled) {
            setResults(matches)
            setOpen(true)
          }
        })
        .catch(() => {
          if (!cancelled) setResults([])
        })
    }, DEBOUNCE_MS)
    return () => {
      cancelled = true
      window.clearTimeout(timeout)
    }
  }, [query])

  useEffect(() => {
    function handleClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div className="provider-name-search" ref={containerRef}>
      <p className="provider-name-search-label">Already know who you&rsquo;re looking for?</p>
      <div className="input-with-icon">
        <SearchIcon width={16} height={16} />
        <input
          type="search"
          placeholder="Search providers by name"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onFocus={() => results.length > 0 && setOpen(true)}
          aria-label="Search providers by name"
        />
      </div>

      {open && results.length > 0 && (
        <ul className="provider-name-search-results">
          {results.map((result) => (
            <li key={result.id}>
              <Link to={`/providers/${result.id}`} onClick={() => setOpen(false)}>
                <span className="provider-name-search-name">{providerDisplayName(result)}</span>
                <span className="provider-name-search-meta">
                  {result.specialtyDisplayName} &middot; {result.city}, {result.stateCode}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {open && results.length === 0 && query.trim().length >= MIN_QUERY_LENGTH && (
        <p className="provider-name-search-empty">No matching providers found.</p>
      )}
    </div>
  )
}

export default ProviderNameSearch
