import type { NetworkEvidenceSummaryDto } from '../api/types'
import { formatDistance } from '../utils/providerDisplay'
import { evidenceStatusCopy } from '../utils/networkEvidenceDisplay'
import { BadgeIcon, CheckIcon, ChevronRightIcon, InfoIcon } from './icons'

interface WhyThisResultProps {
  specialtyDisplayName: string
  npiNumber: string
  distanceMiles?: number | null
  originLabel?: string | null
  networkEvidence?: NetworkEvidenceSummaryDto | null
  coordinatePrecision?: string | null
  /** Practical-fit fact (CLAUDE.md "Practical Fit Summary") -- undefined when not applicable (e.g. no location context at all). */
  hasPhone?: boolean
}

/**
 * Factual-only explanation of why a provider appeared in results. Every line here must be
 * something DocFit AI actually knows and can verify from its own data -- never a score, rank,
 * or "best match" claim.
 */
function WhyThisResult({
  specialtyDisplayName,
  npiNumber,
  distanceMiles,
  originLabel,
  networkEvidence,
  coordinatePrecision,
  hasPhone,
}: WhyThisResultProps) {
  const isApproximate = coordinatePrecision === 'ZIP_CENTROID' || coordinatePrecision === 'CITY_CENTROID'
  return (
    <details className="why-this-result">
      <summary>
        <ChevronRightIcon width={14} height={14} className="why-this-result-chevron" />
        Why this result?
      </summary>
      <ul className="why-this-result-list">
        <li>
          <CheckIcon width={14} height={14} />
          <span>
            <strong>Specialty:</strong> Matches {specialtyDisplayName}
          </span>
        </li>
        {distanceMiles != null && (
          <li>
            <CheckIcon width={14} height={14} />
            <span>
              <strong>Location:</strong> Approximately {formatDistance(distanceMiles)} from{' '}
              {originLabel ?? 'your search location'}
            </span>
          </li>
        )}
        <li>
          <CheckIcon width={14} height={14} />
          <span>
            <strong>Source:</strong> Provider identity found in public NPPES/NPI data (NPI {npiNumber})
          </span>
        </li>
        {isApproximate && (
          <li className="why-this-result-info">
            <InfoIcon width={14} height={14} />
            <span>
              <strong>Location precision:</strong> ZIP-level approximate, not this office's exact address
            </span>
          </li>
        )}
        {hasPhone != null && (
          <li className={hasPhone ? '' : 'why-this-result-info'}>
            {hasPhone ? <CheckIcon width={14} height={14} /> : <InfoIcon width={14} height={14} />}
            <span>
              <strong>Phone:</strong> {hasPhone ? 'Available in the source record' : 'Not listed in the source record'}
            </span>
          </li>
        )}
        {networkEvidence ? (
          <li className={networkEvidence.status === 'EVIDENCE_FOUND' ? '' : 'why-this-result-info'}>
            {networkEvidence.status === 'EVIDENCE_FOUND' ? (
              <CheckIcon width={14} height={14} />
            ) : (
              <InfoIcon width={14} height={14} />
            )}
            <span>
              <strong>Insurance:</strong> {evidenceStatusCopy(networkEvidence.status)}
              {networkEvidence.synthetic ? ' (synthetic demo data)' : ''} -- coverage is not guaranteed
            </span>
          </li>
        ) : (
          <li className="why-this-result-info">
            <InfoIcon width={14} height={14} />
            <span>
              <strong>Insurance:</strong> Coverage is not verified
            </span>
          </li>
        )}
      </ul>
      <p className="why-this-result-footnote">
        <BadgeIcon width={12} height={12} />
        DocFit AI does not rank or score providers by quality -- results are ordered by the criteria you chose.
      </p>
    </details>
  )
}

export default WhyThisResult
