import { describe, expect, it } from 'vitest'
import { evidenceStatusCopy, freshnessCopy, matchMethodCopy } from './networkEvidenceDisplay'
import type { NetworkEvidenceStatusValue } from '../api/types'

const ALL_STATUSES: NetworkEvidenceStatusValue[] = [
  'EVIDENCE_FOUND',
  'NO_EVIDENCE_FOUND',
  'SOURCE_UNAVAILABLE',
  'MATCH_AMBIGUOUS',
  'NOT_CHECKED',
]

describe('networkEvidenceDisplay copy', () => {
  it('never asserts an unqualified coverage/guarantee claim as a status label', () => {
    const bannedAsAClaim = /^covered$|^guaranteed|^free$|\$0/i
    for (const status of ALL_STATUSES) {
      expect(evidenceStatusCopy(status)).not.toMatch(bannedAsAClaim)
    }
  })

  it('NO_EVIDENCE_FOUND is never labeled "out of network"', () => {
    expect(evidenceStatusCopy('NO_EVIDENCE_FOUND')).not.toMatch(/out of network/i)
    expect(evidenceStatusCopy('NO_EVIDENCE_FOUND')).toBe('No directory evidence found')
  })

  it('freshness copy never uses scary false precision beyond the allowed phrases', () => {
    expect(freshnessCopy('FRESH')).toBe('Recently checked')
    expect(freshnessCopy('STALE')).toBe('Evidence may be outdated')
  })

  it('match method copy is always explainable, never hidden as a raw enum code alone', () => {
    expect(matchMethodCopy('NPI_AND_LOCATION')).toBe('NPI + practice location')
    expect(matchMethodCopy('AMBIGUOUS')).toContain('multiple conflicting records')
  })
})
