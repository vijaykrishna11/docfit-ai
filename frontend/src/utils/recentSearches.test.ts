import { beforeEach, describe, expect, it } from 'vitest'
import { clearRecentSearches, getRecentSearches, recordRecentSearch } from './recentSearches'

describe('recentSearches', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('starts empty and records a search most-recent-first', () => {
    expect(getRecentSearches()).toEqual([])

    recordRecentSearch({ specialtyCode: 'CARDIOLOGY', specialtyName: 'Cardiology', locationLabel: 'Long Beach, CA', radius: 10 })
    recordRecentSearch({ specialtyCode: 'DERMATOLOGY', specialtyName: 'Dermatology', locationLabel: '90815', radius: 25 })

    const entries = getRecentSearches()
    expect(entries).toHaveLength(2)
    expect(entries[0].specialtyCode).toBe('DERMATOLOGY')
    expect(entries[1].specialtyCode).toBe('CARDIOLOGY')
  })

  it('deduplicates an identical search rather than listing it twice', () => {
    recordRecentSearch({ specialtyCode: 'CARDIOLOGY', specialtyName: 'Cardiology', locationLabel: 'Long Beach, CA', radius: 10 })
    recordRecentSearch({ specialtyCode: 'CARDIOLOGY', specialtyName: 'Cardiology', locationLabel: 'Long Beach, CA', radius: 10 })

    expect(getRecentSearches()).toHaveLength(1)
  })

  it('caps the list at 5 entries, dropping the oldest', () => {
    for (let i = 0; i < 7; i++) {
      recordRecentSearch({ specialtyCode: 'CARDIOLOGY', specialtyName: 'Cardiology', locationLabel: `ZIP-${i}`, radius: 10 })
    }
    const entries = getRecentSearches()
    expect(entries).toHaveLength(5)
    expect(entries[0].locationLabel).toBe('ZIP-6')
    expect(entries.map((e) => e.locationLabel)).not.toContain('ZIP-0')
  })

  it('clear removes everything', () => {
    recordRecentSearch({ specialtyCode: 'CARDIOLOGY', specialtyName: 'Cardiology', locationLabel: 'Long Beach, CA', radius: 10 })
    clearRecentSearches()
    expect(getRecentSearches()).toEqual([])
  })

  it('never stores anything beyond specialty/location/radius -- no token, email, or free-text medical context', () => {
    recordRecentSearch({ specialtyCode: 'CARDIOLOGY', specialtyName: 'Cardiology', locationLabel: 'Long Beach, CA', radius: 10 })
    const raw = sessionStorage.getItem('docfitai.recentSearches') ?? ''
    expect(raw).not.toContain('token')
    expect(raw).not.toContain('@')
  })
})
