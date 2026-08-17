import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, createShortlist, fetchShortlists } from '../api/client'
import type { ShortlistDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { AlertIcon, FolderIcon } from '../components/icons'

function ShortlistsPage() {
  const [shortlists, setShortlists] = useState<ShortlistDto[] | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [isCreating, setIsCreating] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchShortlists()
      .then((result) => {
        if (!cancelled) setShortlists(result)
      })
      .catch((error: unknown) => {
        if (!cancelled) setErrorMessage(error instanceof ApiError ? error.message : 'Unable to load your shortlists right now.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault()
    const name = newName.trim()
    if (!name) return
    setIsCreating(true)
    try {
      const created = await createShortlist(name)
      setShortlists((prev) => [created, ...(prev ?? [])])
      setNewName('')
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : 'Unable to create this shortlist right now.')
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <div>
          <h1>Shortlists</h1>
          <p className="results-subtext">
            Organize saved providers into named collections -- e.g. &ldquo;Cardiology options&rdquo; or
            &ldquo;Near campus&rdquo;. Shortlist names are private and never shown to anyone else.
          </p>
        </div>

        <form className="create-shortlist-form" onSubmit={handleCreate}>
          <div className="field">
            <label htmlFor="new-shortlist-name">New shortlist</label>
            <input
              id="new-shortlist-name"
              type="text"
              className="plain-select"
              placeholder="e.g. Near campus"
              maxLength={100}
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
            />
          </div>
          <button type="submit" className="primary-button" disabled={!newName.trim() || isCreating}>
            Create shortlist
          </button>
        </form>

        {errorMessage && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={20} height={20} />
            <p>{errorMessage}</p>
          </div>
        )}

        {shortlists == null && !errorMessage && <p className="results-heading-loading">Loading your shortlists…</p>}

        {shortlists != null && shortlists.length === 0 && (
          <div className="state-panel empty-panel">
            <div className="empty-panel-icon">
              <FolderIcon width={22} height={22} />
            </div>
            <h3>No shortlists yet</h3>
            <p className="state-hint">
              Create one above, or add a saved provider to a new shortlist from their profile page.
            </p>
          </div>
        )}

        {shortlists != null && shortlists.length > 0 && (
          <div className="shortlist-grid">
            {shortlists.map((shortlist) => (
              <Link key={shortlist.id} className="shortlist-summary-card" to={`/shortlists/${shortlist.id}`}>
                <FolderIcon width={20} height={20} />
                <h3>{shortlist.name}</h3>
                <span className="shortlist-summary-count">
                  {shortlist.providerCount} provider{shortlist.providerCount === 1 ? '' : 's'}
                </span>
              </Link>
            ))}
          </div>
        )}
      </main>
      <Footer />
    </div>
  )
}

export default ShortlistsPage
