import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  addProviderToShortlist,
  createShortlist,
  fetchShortlistDetail,
  fetchShortlists,
  removeProviderFromShortlist,
} from '../api/client'
import type { ShortlistDto } from '../api/types'
import { CheckIcon, FolderIcon } from './icons'

interface AddToShortlistMenuProps {
  providerId: number
}

/**
 * Optional, one-click-away shortlist assignment (CLAUDE.md "Default Shortlist" -- saving a
 * provider stays a single click; this is a deliberate secondary action, not a dialog forced on
 * every save). Only shown to authenticated users, and only from the provider detail page, not
 * every search-result card, to avoid crowding the card (CLAUDE.md "Card Design V3").
 */
function AddToShortlistMenu({ providerId }: AddToShortlistMenuProps) {
  const [open, setOpen] = useState(false)
  const [shortlists, setShortlists] = useState<ShortlistDto[] | null>(null)
  const [membership, setMembership] = useState<Set<number>>(new Set())
  const [newName, setNewName] = useState('')
  const [busyId, setBusyId] = useState<number | 'new' | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open || shortlists != null) return
    fetchShortlists()
      .then(async (list) => {
        setShortlists(list)
        // Determine which of the user's shortlists already contain this provider. A shortlist
        // count is typically small (single digits), so one detail fetch per shortlist here is
        // bounded, client-side, personal-list-management work -- not the kind of per-provider,
        // per-search-result N+1 the backend search path deliberately avoids.
        const details = await Promise.all(list.map((shortlist) => fetchShortlistDetail(shortlist.id).catch(() => null)))
        const memberOf = new Set<number>()
        details.forEach((detail, index) => {
          if (detail?.providers.some((entry) => entry.providerId === providerId)) {
            memberOf.add(list[index].id)
          }
        })
        setMembership(memberOf)
      })
      .catch(() => setShortlists([]))
  }, [open, shortlists, providerId])

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

  async function toggleMembership(shortlistId: number) {
    setBusyId(shortlistId)
    const inList = membership.has(shortlistId)
    try {
      if (inList) {
        await removeProviderFromShortlist(shortlistId, providerId)
        setMembership((prev) => {
          const next = new Set(prev)
          next.delete(shortlistId)
          return next
        })
      } else {
        await addProviderToShortlist(shortlistId, providerId)
        setMembership((prev) => new Set(prev).add(shortlistId))
      }
    } catch {
      // Non-fatal: the checkbox simply reflects unchanged state if the request failed.
    } finally {
      setBusyId(null)
    }
  }

  async function handleCreate() {
    const name = newName.trim()
    if (!name) return
    setBusyId('new')
    try {
      const created = await createShortlist(name)
      await addProviderToShortlist(created.id, providerId)
      setShortlists((prev) => [created, ...(prev ?? [])])
      setMembership((prev) => new Set(prev).add(created.id))
      setNewName('')
    } catch {
      // Non-fatal: the create form simply stays open for the user to retry.
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="add-to-shortlist-menu" ref={containerRef}>
      <button type="button" className="secondary-button" onClick={() => setOpen((value) => !value)} aria-expanded={open} aria-haspopup="dialog">
        <FolderIcon width={16} height={16} />
        Add to shortlist
      </button>

      {open && (
        <div className="add-to-shortlist-panel" role="dialog" aria-label="Add to shortlist">
          {shortlists == null && <p className="field-hint">Loading your shortlists…</p>}
          {shortlists != null && shortlists.length === 0 && (
            <p className="field-hint">You don&rsquo;t have any shortlists yet -- create one below.</p>
          )}
          {shortlists != null && shortlists.length > 0 && (
            <ul className="add-to-shortlist-list">
              {shortlists.map((shortlist) => {
                const inList = membership.has(shortlist.id)
                return (
                  <li key={shortlist.id}>
                    <label className="practical-filter-checkbox">
                      <input
                        type="checkbox"
                        checked={inList}
                        disabled={busyId === shortlist.id}
                        onChange={() => toggleMembership(shortlist.id)}
                      />
                      {shortlist.name}
                    </label>
                  </li>
                )
              })}
            </ul>
          )}

          <div className="add-to-shortlist-create">
            <input
              type="text"
              className="plain-select"
              placeholder="New shortlist name"
              maxLength={100}
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault()
                  void handleCreate()
                }
              }}
            />
            <button type="button" className="ghost-button" onClick={handleCreate} disabled={!newName.trim() || busyId === 'new'}>
              Create
            </button>
          </div>

          <Link to="/shortlists" className="link-button" onClick={() => setOpen(false)}>
            <CheckIcon width={13} height={13} />
            Manage all shortlists
          </Link>
        </div>
      )}
    </div>
  )
}

export default AddToShortlistMenu
