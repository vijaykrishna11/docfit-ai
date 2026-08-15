import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  ApiError,
  loginAccount,
  logoutAccount,
  refreshAccessToken,
  registerAccount,
  setAccessToken as setClientAccessToken,
  setRefreshHandler,
} from '../api/client'
import type { UserDto } from '../api/types'

interface AuthContextValue {
  user: UserDto | null
  /** True until the initial silent session-restore attempt (via the refresh cookie) finishes. */
  isLoading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => Promise<void>
  setUser: (user: UserDto) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  // Mirrors the in-memory access token held by the api client, so components can react to
  // sign-in/out without reading module-level state directly.
  const accessTokenRef = useRef<string | null>(null)

  const applySession = useCallback((token: string | null, nextUser: UserDto | null) => {
    accessTokenRef.current = token
    setClientAccessToken(token)
    setUser(nextUser)
  }, [])

  useEffect(() => {
    // Registered once so the api client can transparently refresh an expired access token
    // on any 401, not only during the initial mount below.
    setRefreshHandler(async () => {
      try {
        const result = await refreshAccessToken()
        applySession(result.accessToken, result.user)
        return result.accessToken
      } catch {
        applySession(null, null)
        return null
      }
    })
    return () => setRefreshHandler(null)
  }, [applySession])

  useEffect(() => {
    let cancelled = false
    async function restoreSession() {
      try {
        const result = await refreshAccessToken()
        if (!cancelled) {
          applySession(result.accessToken, result.user)
        }
      } catch {
        // No valid refresh cookie (or it's expired/revoked) -- stay signed out. This is the
        // expected path for first-time and already-signed-out visitors, not an error to surface.
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }
    void restoreSession()
    return () => {
      cancelled = true
    }
  }, [applySession])

  const login = useCallback(
    async (email: string, password: string) => {
      const result = await loginAccount(email, password)
      applySession(result.accessToken, result.user)
    },
    [applySession],
  )

  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      const result = await registerAccount(email, password, displayName)
      applySession(result.accessToken, result.user)
    },
    [applySession],
  )

  const logout = useCallback(async () => {
    try {
      await logoutAccount()
    } catch (error) {
      // Best-effort: even if the server call fails (e.g. already-expired session), clear
      // local state so the UI reflects a signed-out user rather than getting stuck.
      if (!(error instanceof ApiError)) {
        throw error
      }
    } finally {
      applySession(null, null)
    }
  }, [applySession])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      login,
      register,
      logout,
      setUser,
    }),
    [user, isLoading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
