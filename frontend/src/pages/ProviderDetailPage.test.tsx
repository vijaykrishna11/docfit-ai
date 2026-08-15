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
  firstName: 'Arun',
  lastName: 'Parvatananeni',
  organizationName: null,
  phone: '562-595-1911',
  addressLine1: '2776 Pacific Ave',
  addressLine2: null,
  city: 'Long Beach',
  stateCode: 'CA',
  postalCode: '90806',
  distanceMiles: 2.5,
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
