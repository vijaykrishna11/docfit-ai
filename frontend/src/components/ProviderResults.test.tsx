import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import ProviderResults from './ProviderResults'
import type { ProviderSearchResultDto } from '../api/types'
import { renderWithProviders } from '../testUtils'

const sampleProvider: ProviderSearchResultDto = {
  id: 1,
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
  taxonomyCode: '207RC0000X',
  specialtyDisplayName: 'Cardiovascular Disease Specialist',
  distanceMiles: 2.5,
}

describe('ProviderResults', () => {
  it('renders provider cards with name, specialty, distance, address, phone, NPI, and working actions', () => {
    renderWithProviders(<ProviderResults status="success" results={[sampleProvider]} originLabel="Long Beach, CA" />)

    expect(screen.getByText('Arun Parvatananeni')).toBeInTheDocument()
    expect(screen.getByText('Cardiovascular Disease Specialist')).toBeInTheDocument()
    expect(screen.getByText('2.5 mi')).toBeInTheDocument()
    expect(screen.getByText(/2776 Pacific Ave/)).toBeInTheDocument()
    expect(screen.getByText('562-595-1911')).toBeInTheDocument()
    expect(screen.getByText(/1538111547/)).toBeInTheDocument()

    const callLink = screen.getByRole('link', { name: /call/i })
    expect(callLink).toHaveAttribute('href', 'tel:5625951911')

    const directionsLink = screen.getByRole('link', { name: /directions/i })
    expect(directionsLink).toHaveAttribute('href', expect.stringContaining('google.com/maps'))
    expect(directionsLink).toHaveAttribute('target', '_blank')
    expect(directionsLink).toHaveAttribute('rel', expect.stringContaining('noopener'))

    expect(screen.getByRole('link', { name: /view details/i })).toHaveAttribute('href', '/providers/1')
    expect(screen.getByRole('checkbox', { name: /compare/i })).toBeInTheDocument()
  })

  it('renders a no-results message when the search succeeds with an empty list', () => {
    renderWithProviders(<ProviderResults status="success" results={[]} originLabel="Long Beach, CA" />)

    expect(screen.getByText(/no providers found/i)).toBeInTheDocument()
  })
})
