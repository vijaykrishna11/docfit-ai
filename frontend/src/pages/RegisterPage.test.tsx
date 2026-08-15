import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import * as client from '../api/client'
import { renderWithProviders } from '../testUtils'
import RegisterPage from './RegisterPage'

const sampleUser = {
  id: 2,
  email: 'new@example.com',
  displayName: null,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('RegisterPage', () => {
  it('registers and redirects home on success', async () => {
    vi.spyOn(client, 'registerAccount').mockResolvedValue({
      accessToken: 'token-abc',
      expiresInSeconds: 900,
      user: sampleUser,
    })
    vi.spyOn(client, 'fetchSavedProviders').mockResolvedValue([])

    renderWithProviders(
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<p>Home page</p>} />
      </Routes>,
      { route: '/register' },
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'securepass1' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => expect(screen.getByText('Home page')).toBeInTheDocument())
    expect(client.registerAccount).toHaveBeenCalledWith('new@example.com', 'securepass1', '')

    vi.restoreAllMocks()
  })

  it('blocks submission with a client-side message when the password is too short', async () => {
    const registerSpy = vi.spyOn(client, 'registerAccount')

    renderWithProviders(
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
      </Routes>,
      { route: '/register' },
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'short' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() =>
      expect(screen.getByText('Password must be at least 8 characters.')).toBeInTheDocument(),
    )
    expect(registerSpy).not.toHaveBeenCalled()

    vi.restoreAllMocks()
  })

  it('surfaces a duplicate-account error from the server', async () => {
    vi.spyOn(client, 'registerAccount').mockRejectedValue(
      new client.ApiError('An account with this email already exists.', 409),
    )

    renderWithProviders(
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
      </Routes>,
      { route: '/register' },
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'existing@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'securepass1' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() =>
      expect(screen.getByText('An account with this email already exists.')).toBeInTheDocument(),
    )

    vi.restoreAllMocks()
  })
})
