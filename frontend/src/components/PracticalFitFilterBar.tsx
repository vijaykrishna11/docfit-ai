import { useEffect, useRef, useState } from 'react'
import type { PracticalFitFilters } from '../api/client'
import { CheckIcon, CloseIcon, FilterIcon } from './icons'

interface PracticalFitFilterBarProps {
  filters: PracticalFitFilters
  onChange: (filters: PracticalFitFilters) => void
  /** Network evidence filter only makes sense once a plan is selected -- otherwise there's nothing to check evidence against. */
  networkEvidenceAvailable: boolean
}

const FILTER_LABELS: Record<keyof PracticalFitFilters, string> = {
  providerType: 'Organization only',
  hasPhone: 'Has phone',
  preciseLocationOnly: 'Precise location',
  networkEvidenceFound: 'Network evidence found',
  multipleLocations: 'Multiple locations',
}

function activeFilterEntries(filters: PracticalFitFilters): [keyof PracticalFitFilters, string][] {
  const entries: [keyof PracticalFitFilters, string][] = []
  if (filters.providerType === 'ORGANIZATION') entries.push(['providerType', FILTER_LABELS.providerType])
  if (filters.providerType === 'INDIVIDUAL') entries.push(['providerType', 'Individual only'])
  if (filters.hasPhone) entries.push(['hasPhone', FILTER_LABELS.hasPhone])
  if (filters.preciseLocationOnly) entries.push(['preciseLocationOnly', FILTER_LABELS.preciseLocationOnly])
  if (filters.networkEvidenceFound) entries.push(['networkEvidenceFound', FILTER_LABELS.networkEvidenceFound])
  if (filters.multipleLocations) entries.push(['multipleLocations', FILTER_LABELS.multipleLocations])
  return entries
}

/**
 * Practical-fit filters (CLAUDE.md "Practical Fit Filter Bar") -- only attributes DocFit actually
 * has. A compact dropdown on desktop, a full-width drawer on mobile (driven by the same markup,
 * styled responsively -- see .practical-filter-panel in App.css).
 */
function PracticalFitFilterBar({ filters, onChange, networkEvidenceAvailable }: PracticalFitFilterBarProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const activeCount = activeFilterEntries(filters).length

  useEffect(() => {
    if (!open) return
    function handleClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClick)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  function update(patch: Partial<PracticalFitFilters>) {
    onChange({ ...filters, ...patch })
  }

  function removeFilter(key: keyof PracticalFitFilters) {
    const next = { ...filters }
    delete next[key]
    onChange(next)
  }

  return (
    <div className="practical-filter-bar">
      <div className="practical-filter-trigger-wrap" ref={containerRef}>
        <button
          type="button"
          className={`secondary-button practical-filter-trigger${activeCount > 0 ? ' is-active' : ''}`}
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-haspopup="dialog"
        >
          <FilterIcon width={15} height={15} />
          Filters
          {activeCount > 0 && <span className="filter-count-badge">{activeCount}</span>}
        </button>

        {open && (
          <div className="practical-filter-panel" role="dialog" aria-label="Practical fit filters">
            <div className="practical-filter-group">
              <span className="practical-filter-group-label">Provider type</span>
              <div className="practical-filter-radio-row">
                <label className="practical-filter-radio">
                  <input
                    type="radio"
                    name="providerType"
                    checked={filters.providerType == null}
                    onChange={() => update({ providerType: undefined })}
                  />
                  Any
                </label>
                <label className="practical-filter-radio">
                  <input
                    type="radio"
                    name="providerType"
                    checked={filters.providerType === 'INDIVIDUAL'}
                    onChange={() => update({ providerType: 'INDIVIDUAL' })}
                  />
                  Individual
                </label>
                <label className="practical-filter-radio">
                  <input
                    type="radio"
                    name="providerType"
                    checked={filters.providerType === 'ORGANIZATION'}
                    onChange={() => update({ providerType: 'ORGANIZATION' })}
                  />
                  Organization
                </label>
              </div>
            </div>

            <label className="practical-filter-checkbox">
              <input
                type="checkbox"
                checked={Boolean(filters.hasPhone)}
                onChange={(event) => update({ hasPhone: event.target.checked || undefined })}
              />
              Has phone number on file
            </label>

            <label className="practical-filter-checkbox">
              <input
                type="checkbox"
                checked={Boolean(filters.preciseLocationOnly)}
                onChange={(event) => update({ preciseLocationOnly: event.target.checked || undefined })}
              />
              More precise location available
            </label>

            <label className="practical-filter-checkbox">
              <input
                type="checkbox"
                checked={Boolean(filters.multipleLocations)}
                onChange={(event) => update({ multipleLocations: event.target.checked || undefined })}
              />
              Has multiple practice locations
            </label>

            <label className={`practical-filter-checkbox${networkEvidenceAvailable ? '' : ' is-disabled'}`}>
              <input
                type="checkbox"
                checked={Boolean(filters.networkEvidenceFound)}
                disabled={!networkEvidenceAvailable}
                onChange={(event) => update({ networkEvidenceFound: event.target.checked || undefined })}
              />
              Network evidence found for selected plan
            </label>
            {!networkEvidenceAvailable && <p className="field-hint">Select an insurance plan above to use this filter.</p>}

            <div className="practical-filter-panel-actions">
              <button type="button" className="ghost-button" onClick={() => onChange({})} disabled={activeCount === 0}>
                Clear all
              </button>
              <button type="button" className="primary-button" onClick={() => setOpen(false)}>
                <CheckIcon width={14} height={14} />
                Done
              </button>
            </div>
          </div>
        )}
      </div>

      {activeCount > 0 && (
        <div className="filter-chips practical-filter-chips">
          {activeFilterEntries(filters).map(([key, label]) => (
            <button key={key} type="button" className="chip chip-clear" onClick={() => removeFilter(key)}>
              <CloseIcon width={12} height={12} />
              {label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default PracticalFitFilterBar
