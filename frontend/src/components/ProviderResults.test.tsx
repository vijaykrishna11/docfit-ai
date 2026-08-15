import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ProviderResults from './ProviderResults'
import type { ProviderSearchResultDto } from '../api/types'

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
  it('renders provider cards with name, specialty, distance, address, phone, and NPI', () => {
    render(<ProviderResults status="success" results={[sampleProvider]} />)

    expect(screen.getByText('Arun Parvatananeni')).toBeInTheDocument()
    expect(screen.getByText('Cardiovascular Disease Specialist')).toBeInTheDocument()
    expect(screen.getByText('2.5 mi')).toBeInTheDocument()
    expect(screen.getByText(/2776 Pacific Ave/)).toBeInTheDocument()
    expect(screen.getByText('562-595-1911')).toBeInTheDocument()
    expect(screen.getByText(/1538111547/)).toBeInTheDocument()
  })

  it('renders a no-results message when the search succeeds with an empty list', () => {
    render(<ProviderResults status="success" results={[]} />)

    expect(screen.getByText(/no providers found/i)).toBeInTheDocument()
  })
})
