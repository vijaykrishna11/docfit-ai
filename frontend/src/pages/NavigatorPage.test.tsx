import { describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import NavigatorPage from './NavigatorPage'
import { renderWithProviders } from '../testUtils'
import * as client from '../api/client'
import type { NavigatorDashboardDto } from '../api/types'

function makeDashboard(): NavigatorDashboardDto {
  return {
    savedCount: 1,
    toContactCount: 1,
    verificationNeededCount: 1,
    providers: [
      {
        providerId: 7,
        npiNumber: '1234567890',
        entityType: 'INDIVIDUAL',
        firstName: 'Amy',
        lastName: 'Provider',
        organizationName: null,
        location: {
          id: 70,
          addressLine1: '1 Test St',
          addressLine2: null,
          city: 'Long Beach',
          stateCode: 'CA',
          postalCode: '90802',
          phone: '562-555-0100',
          latitude: 33.77,
          longitude: -118.19,
          coordinatePrecision: 'ZIP_CENTROID',
        },
        status: 'TO_CONTACT',
        verificationCompleted: 1,
        verificationTotal: 6,
        networkEvidence: null,
        nextAction: 'Contact office',
        savedAt: '2026-01-01T00:00:00Z',
      },
    ],
    shortlists: [
      { id: 3, name: 'Cardiology options', providerCount: 2, toContactCount: 1, contactedCount: 0, createdAt: '', updatedAt: '' },
    ],
    upcomingReminders: [],
    savedPlan: null,
  }
}

describe('NavigatorPage', () => {
  it('shows a factual summary, provider card, and shortlist -- no score or ranking language', async () => {
    vi.spyOn(client, 'fetchNavigatorDashboard').mockResolvedValue(makeDashboard())
    vi.spyOn(client, 'fetchReminders').mockResolvedValue([])
    vi.spyOn(client, 'fetchSavedSearches').mockResolvedValue([])

    renderWithProviders(<NavigatorPage />, { route: '/navigator' })

    await waitFor(() => expect(screen.getByText(/1 provider saved/i)).toBeInTheDocument())
    expect(screen.getByText(/1 still to contact/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Amy Provider' })).toBeInTheDocument()
    expect(screen.getByText(/next: contact office/i)).toBeInTheDocument()
    expect(screen.getByText('Cardiology options')).toBeInTheDocument()

    const bodyText = document.body.textContent ?? ''
    expect(bodyText.toLowerCase()).not.toContain('best match')
    expect(bodyText.toLowerCase()).not.toContain('recommended')
    expect(bodyText.toLowerCase()).not.toContain('top rated')

    vi.restoreAllMocks()
  })

  it('filters the provider list by status', async () => {
    vi.spyOn(client, 'fetchNavigatorDashboard').mockResolvedValue(makeDashboard())
    vi.spyOn(client, 'fetchReminders').mockResolvedValue([])
    vi.spyOn(client, 'fetchSavedSearches').mockResolvedValue([])

    renderWithProviders(<NavigatorPage />, { route: '/navigator' })

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Amy Provider' })).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Contacted' }))
    expect(screen.queryByRole('heading', { name: 'Amy Provider' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'To contact' }))
    expect(screen.getByRole('heading', { name: 'Amy Provider' })).toBeInTheDocument()

    vi.restoreAllMocks()
  })

  it('shows the empty-navigator state when nothing has been saved yet', async () => {
    vi.spyOn(client, 'fetchNavigatorDashboard').mockResolvedValue({
      savedCount: 0,
      toContactCount: 0,
      verificationNeededCount: 0,
      providers: [],
      shortlists: [],
      upcomingReminders: [],
      savedPlan: null,
    })
    vi.spyOn(client, 'fetchReminders').mockResolvedValue([])
    vi.spyOn(client, 'fetchSavedSearches').mockResolvedValue([])

    renderWithProviders(<NavigatorPage />, { route: '/navigator' })

    await waitFor(() => expect(screen.getByText(/your navigator is ready/i)).toBeInTheDocument())

    vi.restoreAllMocks()
  })
})
