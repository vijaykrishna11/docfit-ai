import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import * as client from '../api/client'
import { renderWithProviders } from '../testUtils'
import ProtectedRoute from './ProtectedRoute'

describe('ProtectedRoute', () => {
  it('redirects an anonymous user to sign-in, preserving the intended destination', async () => {
    vi.spyOn(client, 'refreshAccessToken').mockRejectedValue(new client.ApiError('Missing refresh token.', 401))

    renderWithProviders(
      <Routes>
        <Route
          path="/account"
          element={
            <ProtectedRoute>
              <p>Secret account content</p>
            </ProtectedRoute>
          }
        />
        <Route path="/signin" element={<p>Sign in page</p>} />
      </Routes>,
      { route: '/account' },
    )

    await waitFor(() => expect(screen.getByText('Sign in page')).toBeInTheDocument())
    expect(screen.queryByText('Secret account content')).not.toBeInTheDocument()

    vi.restoreAllMocks()
  })

  it('renders protected content once session restoration confirms an authenticated user', async () => {
    vi.spyOn(client, 'refreshAccessToken').mockResolvedValue({
      accessToken: 'token-xyz',
      expiresInSeconds: 900,
      user: { id: 1, email: 'jane@example.com', displayName: 'Jane', createdAt: '2026-01-01T00:00:00Z' },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/account"
          element={
            <ProtectedRoute>
              <p>Secret account content</p>
            </ProtectedRoute>
          }
        />
        <Route path="/signin" element={<p>Sign in page</p>} />
      </Routes>,
      { route: '/account' },
    )

    await waitFor(() => expect(screen.getByText('Secret account content')).toBeInTheDocument())

    vi.restoreAllMocks()
  })
})
