import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import ProviderDetailPage from './ProviderDetailPage'
import { renderWithProviders } from '../testUtils'
import * as client from '../api/client'
import type { ProviderDetailDto } from '../api/types'

const sampleDetail: ProviderDetailDto = {
  id: 42,
  npiNumber: '1538111547',
  entityType: 'INDIVIDUAL',
  firstName: 'Arun',
  lastName: 'Parvatananeni',
  organizationName: null,
  location: {
    id: 1,
    addressLine1: '2776 Pacific Ave',
    addressLine2: null,
    city: 'Long Beach',
    stateCode: 'CA',
    postalCode: '90806',
    phone: '562-595-1911',
    latitude: 33.77,
    longitude: -118.19,
    coordinatePrecision: 'ZIP_CENTROID',
  },
  otherLocations: [],
  distanceMiles: 2.5,
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

describe('ProviderDetailPage', () => {
  it('renders provider name, NPI, taxonomy, and working Call/Directions actions', async () => {
    vi.spyOn(client, 'fetchProviderDetail').mockResolvedValue(sampleDetail)

    renderWithProviders(
      <Routes>
        <Route path="/providers/:id" element={<ProviderDetailPage />} />
      </Routes>,
      { route: '/providers/42' },
    )

    await waitFor(() => expect(screen.getByRole('heading', { name: /arun parvatananeni/i })).toBeInTheDocument())

    expect(screen.getByText('1538111547')).toBeInTheDocument()
    expect(screen.getAllByText('Cardiovascular Disease Specialist')).toHaveLength(2)
    expect(screen.getByRole('button', { name: /back to results/i })).toBeInTheDocument()

    const callLink = screen.getByRole('link', { name: /call/i })
    expect(callLink).toHaveAttribute('href', 'tel:5625951911')

    const directionsLink = screen.getByRole('link', { name: /get directions/i })
    expect(directionsLink).toHaveAttribute('href', expect.stringContaining('google.com/maps'))

    expect(screen.getByText(/sourced from public NPPES\/NPI records/i)).toBeInTheDocument()
    expect(screen.getByText(/insurance coverage is not verified/i)).toBeInTheDocument()

    vi.restoreAllMocks()
  })

  it('shows other practice locations separately, each with its own Call/Directions, never merged into the primary one', async () => {
    const multiLocationDetail: ProviderDetailDto = {
      ...sampleDetail,
      otherLocations: [
        {
          id: 2,
          addressLine1: '456 Second Office',
          addressLine2: null,
          city: 'Lakewood',
          stateCode: 'CA',
          postalCode: '90712',
          phone: '562-555-0002',
          latitude: 33.85,
          longitude: -118.13,
          coordinatePrecision: 'ZIP_CENTROID',
        },
      ],
    }
    vi.spyOn(client, 'fetchProviderDetail').mockResolvedValue(multiLocationDetail)

    renderWithProviders(
      <Routes>
        <Route path="/providers/:id" element={<ProviderDetailPage />} />
      </Routes>,
      { route: '/providers/42' },
    )

    await waitFor(() => expect(screen.getByText('Other locations')).toBeInTheDocument())
    expect(screen.getByText(/456 Second Office/)).toBeInTheDocument()
    // Two "Call" links: one for the primary location, one for the other office.
    expect(screen.getAllByRole('link', { name: /call/i })).toHaveLength(2)
    expect(screen.getAllByRole('link', { name: /directions/i }).length).toBeGreaterThanOrEqual(2)

    vi.restoreAllMocks()
  })

  it('shows a not-found message for an unknown provider', async () => {
    vi.spyOn(client, 'fetchProviderDetail').mockRejectedValue(new client.ApiError('Not found', 404))

    renderWithProviders(
      <Routes>
        <Route path="/providers/:id" element={<ProviderDetailPage />} />
      </Routes>,
      { route: '/providers/999999' },
    )

    await waitFor(() => expect(screen.getByText(/provider not found/i)).toBeInTheDocument())

    vi.restoreAllMocks()
  })
})
