import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, vi } from 'vitest'

// AuthProvider silently attempts a session-restore fetch on mount (POST /api/auth/refresh).
// Tests don't run a backend, so stub fetch to fail fast instead of letting real requests hang;
// individual tests that need real client behavior mock the specific api/client function instead.
beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockRejectedValue(new Error('network disabled in tests')),
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
