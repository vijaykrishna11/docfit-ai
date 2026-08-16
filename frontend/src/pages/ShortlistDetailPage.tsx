import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, deleteShortlist, fetchShortlistDetail, removeProviderFromShortlist, renameShortlist } from '../api/client'
import type { ShortlistDetailDto, ShortlistProviderDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { AlertIcon, BadgeIcon, ChevronLeftIcon, DirectionsIcon, LocationIcon, PhoneIcon } from '../components/icons'
import ShareSelectedProvidersButton from '../components/ShareSelectedProvidersButton'
import { directionsUrl, formattedAddress, initialsFor, providerDisplayName, telHref } from '../utils/providerDisplay'

function ShortlistDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [detail, setDetail] = useState<ShortlistDetailDto | null>(null)
  const [status, setStatus] = useState<'loading' | 'success' | 'error' | 'not-found'>('loading')
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)
  const [renaming, setRenaming] = useState(false)
  const [renameValue, setRenameValue] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())

  function load(): (() => void) | undefined {
    if (!id) return undefined
    let cancelled = false
    setStatus('loading')
    fetchShortlistDetail(Number(id))
      .then((result) => {
        if (cancelled) return
        setDetail(result)
        setRenameValue(result.name)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 404) {
          setStatus('not-found')
        } else {
          setStatus('error')
          setErrorMessage(error instanceof ApiError ? error.message : 'Unable to load this shortlist right now.')
        }
      })
    return () => {
      cancelled = true
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- standard fetch-on-param-change pattern
    return load()
    // load is intentionally omitted -- it's redefined every render but only depends on `id`,
    // which is already listed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  async function handleRename(event: React.FormEvent) {
    event.preventDefault()
    if (!detail || !renameValue.trim()) return
    try {
      const updated = await renameShortlist(detail.id, renameValue.trim())
      setDetail({ ...detail, name: updated.name })
      setRenaming(false)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : 'Unable to rename this shortlist right now.')
    }
  }

  async function handleDelete() {
    if (!detail) return
    try {
      await deleteShortlist(detail.id)
      navigate('/shortlists')
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : 'Unable to delete this shortlist right now.')
    }
  }

  async function handleRemoveProvider(providerId: number) {
    if (!detail) return
    try {
      await removeProviderFromShortlist(detail.id, providerId)
      setDetail({ ...detail, providers: detail.providers.filter((p) => p.providerId !== providerId) })
      setSelectedIds((prev) => {
        const next = new Set(prev)
        next.delete(providerId)
        return next
      })
    } catch {
      // Non-fatal: the list simply stays unchanged if the request failed.
    }
  }

  function toggleSelection(providerId: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(providerId)) next.delete(providerId)
      else next.add(providerId)
      return next
    })
  }

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <Link to="/shortlists" className="back-link">
          <ChevronLeftIcon width={16} height={16} />
          Back to shortlists
        </Link>

        {status === 'loading' && <p className="results-heading-loading">Loading shortlist…</p>}

        {status === 'not-found' && (
          <div className="state-panel empty-panel">
            <h3>Shortlist not found</h3>
            <p>This shortlist doesn&rsquo;t exist, or isn&rsquo;t yours.</p>
          </div>
        )}

        {status === 'error' && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={22} height={22} />
            <p>{errorMessage}</p>
          </div>
        )}

        {status === 'success' && detail && (
          <>
            <div className="shortlist-detail-header">
              <div>
                {renaming ? (
                  <form className="shortlist-rename-form" onSubmit={handleRename}>
                    <input
                      type="text"
                      className="plain-select"
                      maxLength={100}
                      value={renameValue}
                      onChange={(event) => setRenameValue(event.target.value)}
                      autoFocus
                    />
                    <button type="submit" className="primary-button" disabled={!renameValue.trim()}>
                      Save
                    </button>
                    <button type="button" className="ghost-button" onClick={() => setRenaming(false)}>
                      Cancel
                    </button>
                  </form>
                ) : (
                  <h1>{detail.name}</h1>
                )}
                <p className="results-subtext">
                  {detail.providers.length} provider{detail.providers.length === 1 ? '' : 's'}
                </p>
              </div>

              {!renaming && (
                <div className="shortlist-detail-actions">
                  <button type="button" className="secondary-button" onClick={() => setRenaming(true)}>
                    Rename
                  </button>
                  <ShareSelectedProvidersButton selectedIds={Array.from(selectedIds)} />
                  {!confirmingDelete ? (
                    <button type="button" className="secondary-button" onClick={() => setConfirmingDelete(true)}>
                      Delete shortlist
                    </button>
                  ) : (
                    <div className="delete-confirm-row">
                      <span>Delete this shortlist?</span>
                      <div className="delete-confirm-actions">
                        <button type="button" className="secondary-button" onClick={() => setConfirmingDelete(false)}>
                          Cancel
                        </button>
                        <button type="button" className="primary-button danger-button-solid" onClick={handleDelete}>
                          Yes, delete
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {detail.providers.length === 0 ? (
              <div className="state-panel empty-panel">
                <h3>This shortlist is empty</h3>
                <p className="state-hint">Add a saved provider to it from their profile page.</p>
              </div>
            ) : (
              <ul className="provider-list">
                {detail.providers.map((provider) => (
                  <ShortlistProviderCard
                    key={provider.providerId}
                    provider={provider}
                    selected={selectedIds.has(provider.providerId)}
                    onToggleSelect={() => toggleSelection(provider.providerId)}
                    onRemove={() => handleRemoveProvider(provider.providerId)}
                  />
                ))}
              </ul>
            )}
          </>
        )}
      </main>
      <Footer />
    </div>
  )
}

function ShortlistProviderCard({
  provider,
  selected,
  onToggleSelect,
  onRemove,
}: {
  provider: ShortlistProviderDto
  selected: boolean
  onToggleSelect: () => void
  onRemove: () => void
}) {
  const name = providerDisplayName(provider)
  const location = provider.location

  return (
    <li className={`provider-card${selected ? ' is-selected' : ''}`}>
      <div className="provider-card-top">
        <label className="compare-checkbox" style={{ marginRight: 4 }}>
          <input type="checkbox" checked={selected} onChange={onToggleSelect} aria-label={`Select ${name} to share`} />
          <span className="compare-checkbox-box" aria-hidden="true" />
        </label>
        <div className="avatar" aria-hidden="true">
          {initialsFor(name)}
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
        {location?.phone && (
          <p className="detail">
            <PhoneIcon width={16} height={16} />
            <span>{location.phone}</span>
          </p>
        )}
        <p className="npi">
          <BadgeIcon width={14} height={14} />
          <span>NPI {provider.npiNumber}</span>
        </p>
      </div>
      <div className="provider-card-actions">
        <div className="provider-card-buttons">
          {location?.phone && (
            <a className="ghost-button" href={telHref(location.phone)}>
              <PhoneIcon width={14} height={14} />
              Call
            </a>
          )}
          {location && (
            <a className="ghost-button" href={directionsUrl(location)} target="_blank" rel="noopener noreferrer">
              <DirectionsIcon width={14} height={14} />
              Directions
            </a>
          )}
          <Link className="ghost-button" to={`/providers/${provider.providerId}`}>
            View details
          </Link>
          <button type="button" className="ghost-button" onClick={onRemove}>
            Remove
          </button>
        </div>
      </div>
    </li>
  )
}

export default ShortlistDetailPage
