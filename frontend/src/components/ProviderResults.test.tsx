import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import ProviderResults from './ProviderResults'
import type { ProviderSearchResultDto } from '../api/types'
import { renderWithProviders } from '../testUtils'

const sampleProvider: ProviderSearchResultDto = {
  id: 1,
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
  taxonomyCode: '207RC0000X',
  specialtyDisplayName: 'Cardiovascular Disease Specialist',
  distanceMiles: 2.5,
  networkEvidence: null,
  locationCount: 1,
}

describe('ProviderResults', () => {
  it('renders provider cards with name, specialty, distance, address, phone, NPI, and working actions', () => {
    renderWithProviders(<ProviderResults status="success" results={[sampleProvider]} originLabel="Long Beach, CA" />)

    expect(screen.getByText('Arun Parvatananeni')).toBeInTheDocument()
    expect(screen.getByText('Cardiovascular Disease Specialist')).toBeInTheDocument()
    expect(screen.getByText('2.5 mi')).toBeInTheDocument()
    expect(screen.getByText(/2776 Pacific Ave/)).toBeInTheDocument()
    expect(screen.getByText('562-595-1911')).toBeInTheDocument()
    expect(screen.getByText('NPI 1538111547')).toBeInTheDocument()

    const callLink = screen.getByRole('link', { name: /call/i })
    expect(callLink).toHaveAttribute('href', 'tel:5625951911')

    const directionsLink = screen.getByRole('link', { name: /directions/i })
    expect(directionsLink).toHaveAttribute('href', expect.stringContaining('google.com/maps'))
    expect(directionsLink).toHaveAttribute('target', '_blank')
    expect(directionsLink).toHaveAttribute('rel', expect.stringContaining('noopener'))

    expect(screen.getByRole('link', { name: /view details/i })).toHaveAttribute('href', '/providers/1')
    expect(screen.getByRole('checkbox', { name: /compare/i })).toBeInTheDocument()
  })

  it('shows only factual, verifiable reasons in "Why this result?" -- never a score or ranking claim', () => {
    renderWithProviders(<ProviderResults status="success" results={[sampleProvider]} originLabel="Long Beach, CA" />)

    expect(screen.getByText(/matches cardiovascular disease specialist/i)).toBeInTheDocument()
    expect(screen.getByText(/approximately 2\.5 mi from long beach, ca/i)).toBeInTheDocument()
    expect(screen.getByText(/found in public nppes\/npi data/i)).toBeInTheDocument()
    expect(screen.getByText(/coverage is not verified/i)).toBeInTheDocument()
    expect(screen.queryByText(/top match|% match|quality score|recommended provider/i)).not.toBeInTheDocument()
  })

  it('renders a no-results message when the search succeeds with an empty list', () => {
    renderWithProviders(<ProviderResults status="success" results={[]} originLabel="Long Beach, CA" />)

    expect(screen.getByText(/no providers found/i)).toBeInTheDocument()
  })

  it('never renders an unqualified coverage claim as the evidence status label, in any status', () => {
    // Scoped to the status label itself (not surrounding explanatory prose, where a phrase like
    // "doesn't necessarily mean out of network" is correct, intentional hedging).
    const bannedAsAClaim = /^covered$|^guaranteed/i
    const statuses: Array<ProviderSearchResultDto['networkEvidence']> = [
      { status: 'EVIDENCE_FOUND', freshness: 'FRESH', planName: 'Demo PPO', networkName: 'Demo Network', synthetic: true, checkedAt: new Date().toISOString() },
      { status: 'NO_EVIDENCE_FOUND', freshness: null, planName: 'Demo PPO', networkName: 'Demo Network', synthetic: true, checkedAt: null },
      { status: 'SOURCE_UNAVAILABLE', freshness: null, planName: 'Demo PPO', networkName: null, synthetic: false, checkedAt: null },
    ]

    for (const networkEvidence of statuses) {
      const { unmount } = renderWithProviders(
        <ProviderResults
          status="success"
          results={[{ ...sampleProvider, networkEvidence }]}
          originLabel="Long Beach, CA"
          planId={1}
        />,
      )
      const labelText =
        networkEvidence!.status === 'EVIDENCE_FOUND'
          ? 'network evidence found'
          : networkEvidence!.status === 'NO_EVIDENCE_FOUND'
            ? 'no directory evidence found'
            : 'verification source unavailable'
      const labels = screen.getAllByText(new RegExp(labelText, 'i'))
      expect(labels.length).toBeGreaterThan(0)
      for (const label of labels) {
        expect(label.textContent ?? '').not.toMatch(bannedAsAClaim)
      }
      unmount()
    }
  })
})
