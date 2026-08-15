import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

const MAX_COMPARE = 3

interface CompareContextValue {
  selectedIds: number[]
  toggle: (id: number) => void
  clear: () => void
  isSelected: (id: number) => boolean
  isFull: boolean
}

const CompareContext = createContext<CompareContextValue | null>(null)

export function CompareProvider({ children }: { children: ReactNode }) {
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  const toggle = useCallback((id: number) => {
    setSelectedIds((previous) => {
      if (previous.includes(id)) {
        return previous.filter((existingId) => existingId !== id)
      }
      if (previous.length >= MAX_COMPARE) {
        return previous
      }
      return [...previous, id]
    })
  }, [])

  const clear = useCallback(() => setSelectedIds([]), [])

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
