import { useState } from 'react'
import { CheckIcon, ShareIcon } from './icons'
import { MAX_SHARED_PROVIDERS } from '../pages/SharedProvidersPage'

interface ShareSelectedProvidersButtonProps {
  selectedIds: number[]
}

/** Builds a public /share/providers link from selected provider ids and copies it (CLAUDE.md "Share Selected Providers"). No backend sharing table -- the ids in the URL are the entire share. */
function ShareSelectedProvidersButton({ selectedIds }: ShareSelectedProvidersButtonProps) {
  const [copied, setCopied] = useState(false)
  const disabled = selectedIds.length === 0

  async function handleClick() {
    const ids = selectedIds.slice(0, MAX_SHARED_PROVIDERS).join(',')
    const url = `${window.location.origin}/share/providers?ids=${ids}`
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2500)
    } catch {
      // Clipboard access can be denied by the browser -- fail quietly rather than show a raw error.
    }
  }

  return (
    <button type="button" className={`secondary-button${copied ? ' is-success' : ''}`} onClick={handleClick} disabled={disabled}>
      {copied ? (
        <>
          <CheckIcon width={14} height={14} />
          Link copied
        </>
      ) : (
        <>
          <ShareIcon width={14} height={14} />
          Share selected ({selectedIds.length})
        </>
      )}
    </button>
  )
}

export default ShareSelectedProvidersButton
