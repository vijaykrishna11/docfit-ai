import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import * as client from '../api/client'
import { renderWithProviders } from '../testUtils'
import SignInPage from './SignInPage'

const sampleUser = {
  id: 1,
  email: 'jane@example.com',
  displayName: 'Jane Doe',
  createdAt: '2026-01-01T00:00:00Z',
}

describe('SignInPage', () => {
  it('signs in and redirects to the requested page', async () => {
    vi.spyOn(client, 'loginAccount').mockResolvedValue({
      accessToken: 'token-123',
      expiresInSeconds: 900,
      user: sampleUser,
    })
    vi.spyOn(client, 'fetchSavedProviders').mockResolvedValue([])

    renderWithProviders(
      <Routes>
        <Route path="/signin" element={<SignInPage />} />
        <Route path="/account" element={<p>Account page</p>} />
      </Routes>,
      { route: '/signin?redirect=%2Faccount' },
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'jane@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(screen.getByText('Account page')).toBeInTheDocument())
    expect(client.loginAccount).toHaveBeenCalledWith('jane@example.com', 'password123')

    vi.restoreAllMocks()
  })

  it('shows the server error message on failed sign-in', async () => {
    vi.spyOn(client, 'loginAccount').mockRejectedValue(new client.ApiError('Invalid email or password.', 401))

    renderWithProviders(
      <Routes>
        <Route path="/signin" element={<SignInPage />} />
      </Routes>,
      { route: '/signin' },
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'jane@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrongpassword' } })
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(screen.getByText('Invalid email or password.')).toBeInTheDocument())

    vi.restoreAllMocks()
  })

  it('does not offer an OAuth or forgot-password link', () => {
    renderWithProviders(
      <Routes>
        <Route path="/signin" element={<SignInPage />} />
      </Routes>,
      { route: '/signin' },
    )

    expect(screen.queryByText(/forgot password/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/google/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/sign in with/i)).not.toBeInTheDocument()
  })
})
