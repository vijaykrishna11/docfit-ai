import { describe, expect, it } from 'vitest'
import { providerDisplayName } from './providerDisplay'

describe('providerDisplayName', () => {
  it('uses the organization name for organization providers, never "null null"', () => {
    const name = providerDisplayName({ organizationName: 'Long Beach Cardiology Medical Group', firstName: null, lastName: null })
    expect(name).not.toMatch(/null/i)
    expect(name.toLowerCase()).toContain('long beach cardiology')
  })

  it('uses first/last name for individual providers', () => {
    const name = providerDisplayName({ organizationName: null, firstName: 'Jane', lastName: 'Doe' })
    expect(name).toBe('Jane Doe')
  })

  it('falls back to a safe label when no name data exists at all, never "null null"', () => {
    const name = providerDisplayName({ organizationName: null, firstName: null, lastName: null })
    expect(name).not.toMatch(/null/i)
  })
})
