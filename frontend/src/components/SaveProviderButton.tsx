import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useSavedProviders } from '../context/SavedProvidersContext'
import { HeartIcon } from './icons'

interface SaveProviderButtonProps {
  providerId: number
  /** 'icon' is a compact circular toggle for provider cards; 'labeled' shows text, for the detail page. */
  variant?: 'icon' | 'labeled'
}

function SaveProviderButton({ providerId, variant = 'icon' }: SaveProviderButtonProps) {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const { isSaved, toggleSaved } = useSavedProviders()
  const navigate = useNavigate()
  const location = useLocation()
  const [isBusy, setIsBusy] = useState(false)

  const saved = isSaved(providerId)

  async function handleClick() {
    // Session restore (silent refresh) hasn't resolved yet -- avoid misreading a still-loading
    // session as "anonymous" and wrongly redirecting an already-signed-in user to /signin.
    if (isAuthLoading) {
      return
    }
    if (!isAuthenticated) {
      const redirectTo = `${location.pathname}${location.search}`
      navigate(`/signin?redirect=${encodeURIComponent(redirectTo)}&saveProvider=${providerId}`)
      return
    }
    setIsBusy(true)
    try {
      await toggleSaved(providerId)
    } catch {
      // Non-fatal: the button simply reflects unchanged state if the request failed.
    } finally {
      setIsBusy(false)
    }
  }

  const label = saved ? 'Saved' : 'Save'

  if (variant === 'labeled') {
    return (
      <button
        type="button"
        className={`secondary-button${saved ? ' is-success' : ''}`}
        onClick={handleClick}
        disabled={isBusy || isAuthLoading}
        aria-pressed={saved}
      >
        <HeartIcon width={16} height={16} fill={saved ? 'currentColor' : 'none'} />
        {saved ? 'Saved to your list' : 'Save provider'}
      </button>
    )
  }

  return (
    <button
      type="button"
      className={`save-toggle${saved ? ' is-saved' : ''}`}
      onClick={handleClick}
      disabled={isBusy || isAuthLoading}
      aria-pressed={saved}
      aria-label={saved ? 'Remove from saved providers' : 'Save this provider'}
      title={label}
    >
      <HeartIcon width={17} height={17} fill={saved ? 'currentColor' : 'none'} />
    </button>
  )
}

export default SaveProviderButton
