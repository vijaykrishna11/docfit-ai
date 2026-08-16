import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import ComparePage from './ComparePage'
import { renderWithProviders } from '../testUtils'
import * as client from '../api/client'
import type { ProviderDetailDto } from '../api/types'

function makeDetail(id: number, name: string): ProviderDetailDto {
  return {
    id,
    npiNumber: `100000000${id}`,
    entityType: 'INDIVIDUAL',
    firstName: name,
    lastName: 'Provider',
    organizationName: null,
    location: {
      id: id * 10,
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
    otherLocations: [],
    distanceMiles: 1.2,
    importedAt: '2026-01-01T00:00:00Z',
    taxonomies: [
      {
        taxonomyCode: '207RC0000X',
        classification: 'Internal Medicine',
        specialization: 'Cardiovascular Disease',
        displayName: 'Cardiovascular Disease Specialist',
        primaryTaxonomy: true,
      },
    ],
  }
}

describe('ComparePage', () => {
  it('fetches and renders a comparison table for the ids in the URL', async () => {
    vi.spyOn(client, 'fetchProviderDetail').mockImplementation((id) =>
      Promise.resolve(makeDetail(id, id === 1 ? 'Amy' : 'Zed')),
    )

    renderWithProviders(<ComparePage />, { route: '/compare?ids=1,2' })

    await waitFor(() => expect(screen.getByRole('columnheader', { name: /amy provider/i })).toBeInTheDocument())

    expect(screen.getByRole('columnheader', { name: /zed provider/i })).toBeInTheDocument()
    expect(screen.getAllByText('Cardiovascular Disease Specialist').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText(/factual navigation data only/i)).toBeInTheDocument()

    vi.restoreAllMocks()
  })

  it('shows a friendly message when no providers are selected', () => {
    renderWithProviders(<ComparePage />, { route: '/compare' })

    expect(screen.getByText(/no providers selected/i)).toBeInTheDocument()
  })
})
