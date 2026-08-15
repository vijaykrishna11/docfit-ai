import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import * as client from '../api/client'
import { renderWithProviders } from '../testUtils'
import SaveProviderButton from './SaveProviderButton'

describe('SaveProviderButton', () => {
  it('redirects an anonymous user to sign-in with the pending save preserved, without saving', async () => {
    vi.spyOn(client, 'refreshAccessToken').mockRejectedValue(new client.ApiError('Missing refresh token.', 401))
    const saveSpy = vi.spyOn(client, 'saveProvider')

    renderWithProviders(
      <Routes>
        <Route path="/providers/42" element={<SaveProviderButton providerId={42} />} />
        <Route path="/signin" element={<p>Sign in page</p>} />
      </Routes>,
      { route: '/providers/42' },
    )

    await waitFor(() => expect(screen.getByRole('button')).not.toBeDisabled())
    fireEvent.click(screen.getByRole('button'))

    await waitFor(() => expect(screen.getByText('Sign in page')).toBeInTheDocument())
    expect(saveSpy).not.toHaveBeenCalled()

    vi.restoreAllMocks()
  })

  it('saves the provider directly for an authenticated user', async () => {
    vi.spyOn(client, 'refreshAccessToken').mockResolvedValue({
      accessToken: 'token-xyz',
      expiresInSeconds: 900,
      user: { id: 1, email: 'jane@example.com', displayName: 'Jane', createdAt: '2026-01-01T00:00:00Z' },
    })
    vi.spyOn(client, 'fetchSavedProviders').mockResolvedValue([])
    const saveSpy = vi.spyOn(client, 'saveProvider').mockResolvedValue(undefined)

    renderWithProviders(
      <Routes>
        <Route path="/providers/42" element={<SaveProviderButton providerId={42} />} />
        <Route path="/signin" element={<p>Sign in page</p>} />
      </Routes>,
      { route: '/providers/42' },
    )

    await waitFor(() => expect(screen.getByRole('button')).not.toBeDisabled())
    fireEvent.click(screen.getByRole('button'))

    await waitFor(() => expect(saveSpy).toHaveBeenCalledWith(42))
    expect(screen.queryByText('Sign in page')).not.toBeInTheDocument()

    vi.restoreAllMocks()
  })
})
