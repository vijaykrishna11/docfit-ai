import type { NetworkEvidenceFreshnessValue, NetworkEvidenceStatusValue } from '../api/types'

/**
 * Every string here is deliberately factual/hedged -- CLAUDE.md's insurance-language rules.
 * Never "covered," "in network," "guaranteed," or "$0 visit." NO_EVIDENCE_FOUND is never
 * rendered as "out of network."
 */
export function evidenceStatusCopy(status: NetworkEvidenceStatusValue): string {
  switch (status) {
    case 'EVIDENCE_FOUND':
      return 'Network evidence found'
    case 'NO_EVIDENCE_FOUND':
      return 'No directory evidence found'
    case 'SOURCE_UNAVAILABLE':
      return 'Verification source unavailable'
    case 'MATCH_AMBIGUOUS':
      return 'Possible network match (unconfirmed)'
    case 'NOT_CHECKED':
      return 'Not yet checked'
    default:
      return 'Evidence unavailable'
  }
}

export function evidenceStatusHint(status: NetworkEvidenceStatusValue): string {
  switch (status) {
    case 'EVIDENCE_FOUND':
      return 'Listed in a provider directory for this plan/network.'
    case 'NO_EVIDENCE_FOUND':
      return "This doesn't necessarily mean out of network -- the directory can be incomplete, stale, or location-specific."
    case 'SOURCE_UNAVAILABLE':
      return 'The verification source could not be reached. Provider search is unaffected.'
    case 'MATCH_AMBIGUOUS':
      return 'The source returned more than one conflicting record for this provider.'
    case 'NOT_CHECKED':
      return "DocFit AI hasn't checked this provider against this plan yet."
    default:
      return 'Confirm with your insurer.'
  }
}

export function freshnessCopy(freshness: NetworkEvidenceFreshnessValue): string {
  switch (freshness) {
    case 'FRESH':
      return 'Recently checked'
    case 'AGING':
      return 'Checked a while ago'
    case 'STALE':
      return 'Evidence may be outdated'
    default:
      return ''
  }
}

export function matchMethodCopy(matchMethod: string): string {
  switch (matchMethod) {
    case 'NPI_EXACT':
      return 'NPI match'
    case 'NPI_AND_LOCATION':
      return 'NPI + practice location'
    case 'NPI_AND_POSTAL_CODE':
      return 'NPI + postal code'
    case 'ORGANIZATION_NPI':
      return 'Organization NPI'
    case 'AMBIGUOUS':
      return 'Ambiguous (multiple conflicting records)'
    default:
      return matchMethod
  }
}

export function formatCheckedAt(checkedAt: string): string {
  const date = new Date(checkedAt)
  const days = Math.floor((Date.now() - date.getTime()) / (1000 * 60 * 60 * 24))
  const dateLabel = date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
  if (days <= 0) return `${dateLabel} (today)`
  if (days === 1) return `${dateLabel} (1 day ago)`
  return `${dateLabel} (${days} days ago)`
}
