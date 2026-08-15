import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchSavedProviders, saveProvider, unsaveProvider } from '../api/client'
import type { SavedProviderDto } from '../api/types'
import { useAuth } from './AuthContext'

interface SavedProvidersContextValue {
  savedProviders: SavedProviderDto[]
  savedIds: Set<number>
  isSaved: (providerId: number) => boolean
  /** Toggles save state for an authenticated user. Callers must check isAuthenticated first. */
  toggleSaved: (providerId: number) => Promise<void>
  /** Re-fetches the saved-provider list, e.g. after completing a save that happened outside toggleSaved. */
  refresh: () => Promise<void>
  isLoading: boolean
}

const SavedProvidersContext = createContext<SavedProvidersContextValue | null>(null)

export function SavedProvidersProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const [savedProviders, setSavedProviders] = useState<SavedProviderDto[]>([])
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    if (!isAuthenticated) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- clear stale saved-provider state on sign-out
      setSavedProviders([])
      return
    }
    let cancelled = false
    setIsLoading(true)
    fetchSavedProviders()
      .then((results) => {
        if (!cancelled) setSavedProviders(results)
      })
      .catch(() => {
        // Non-fatal: saved-state simply won't be reflected until the next successful fetch.
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [isAuthenticated])

  const savedIds = useMemo(() => new Set(savedProviders.map((entry) => entry.providerId)), [savedProviders])

  const refresh = useCallback(async () => {
    if (!isAuthenticated) return
    const results = await fetchSavedProviders()
    setSavedProviders(results)
  }, [isAuthenticated])

  const toggleSaved = useCallback(
    async (providerId: number) => {
      if (savedIds.has(providerId)) {
        await unsaveProvider(providerId)
        setSavedProviders((previous) => previous.filter((entry) => entry.providerId !== providerId))
      } else {
        await saveProvider(providerId)
        const refreshed = await fetchSavedProviders()
        setSavedProviders(refreshed)
      }
    },
    [savedIds],
  )

  const value = useMemo<SavedProvidersContextValue>(
    () => ({
      savedProviders,
      savedIds,
      isSaved: (providerId: number) => savedIds.has(providerId),
      toggleSaved,
      refresh,
      isLoading,
    }),
    [savedProviders, savedIds, toggleSaved, refresh, isLoading],
  )

  return <SavedProvidersContext.Provider value={value}>{children}</SavedProvidersContext.Provider>
}

export function useSavedProviders(): SavedProvidersContextValue {
  const context = useContext(SavedProvidersContext)
  if (!context) {
    throw new Error('useSavedProviders must be used within a SavedProvidersProvider')
  }
  return context
}
