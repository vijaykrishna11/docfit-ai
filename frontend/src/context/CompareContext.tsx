import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

const MAX_COMPARE = 3
// Session-only, not persistent across browser restarts, and never sent to the server --
// this is purely a client-side convenience so a comparison survives an accidental reload.
const STORAGE_KEY = 'docfitai.compareSelection'

interface CompareContextValue {
  selectedIds: number[]
  toggle: (id: number) => void
  clear: () => void
  isSelected: (id: number) => boolean
  isFull: boolean
}

const CompareContext = createContext<CompareContextValue | null>(null)

function readStoredSelection(): number[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((value): value is number => typeof value === 'number') : []
  } catch {
    return []
  }
}

export function CompareProvider({ children }: { children: ReactNode }) {
  const [selectedIds, setSelectedIds] = useState<number[]>(readStoredSelection)

  const persist = useCallback((ids: number[]) => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(ids))
    } catch {
      // sessionStorage can be unavailable (private browsing, quota); comparison still works in-memory.
    }
  }, [])

  const toggle = useCallback(
    (id: number) => {
      setSelectedIds((previous) => {
        const next = previous.includes(id)
          ? previous.filter((existingId) => existingId !== id)
          : previous.length >= MAX_COMPARE
            ? previous
            : [...previous, id]
        persist(next)
        return next
      })
    },
    [persist],
  )

  const clear = useCallback(() => {
    setSelectedIds([])
    persist([])
  }, [persist])

  const value = useMemo<CompareContextValue>(
    () => ({
      selectedIds,
      toggle,
      clear,
      isSelected: (id: number) => selectedIds.includes(id),
      isFull: selectedIds.length >= MAX_COMPARE,
    }),
    [selectedIds, toggle, clear],
  )

  return <CompareContext.Provider value={value}>{children}</CompareContext.Provider>
}

export function useCompare(): CompareContextValue {
  const context = useContext(CompareContext)
  if (!context) {
    throw new Error('useCompare must be used within a CompareProvider')
  }
  return context
}
