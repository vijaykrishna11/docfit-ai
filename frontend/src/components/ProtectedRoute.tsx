import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div className="page">
        <main className="container main-content">
          <p className="results-heading-loading">Loading…</p>
        </main>
      </div>
    )
  }

  if (!isAuthenticated) {
    const redirectTo = `${location.pathname}${location.search}`
    return <Navigate to={`/signin?redirect=${encodeURIComponent(redirectTo)}`} replace />
  }

  return <>{children}</>
}

export default ProtectedRoute
