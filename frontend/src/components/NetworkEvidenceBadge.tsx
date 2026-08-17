import { useState } from 'react'
import type { NetworkEvidenceSummaryDto } from '../api/types'
import NetworkEvidenceDrawer, { NetworkEvidenceStatusIcon } from './NetworkEvidenceDrawer'
import { evidenceStatusCopy, evidenceStatusHint, freshnessCopy } from '../utils/networkEvidenceDisplay'

interface NetworkEvidenceBadgeProps {
  providerId: number
  planId: number
  locationId?: number
  evidence: NetworkEvidenceSummaryDto
}

/** Compact insurance panel for a provider card/result (CLAUDE.md 32). */
function NetworkEvidenceBadge({ providerId, planId, locationId, evidence }: NetworkEvidenceBadgeProps) {
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <div className={`network-evidence-badge evidence-status-${evidence.status.toLowerCase()}`}>
      <div className="network-evidence-badge-summary">
        <NetworkEvidenceStatusIcon status={evidence.status} />
        <span>{evidenceStatusCopy(evidence.status)}</span>
        {evidence.freshness && <span className="evidence-freshness"> · {freshnessCopy(evidence.freshness)}</span>}
        {evidence.synthetic && <span className="chip chip-demo">Synthetic demo data</span>}
      </div>
      <p className="network-evidence-badge-hint">{evidenceStatusHint(evidence.status)}</p>
      <button type="button" className="link-button" onClick={() => setDrawerOpen(true)}>
        View evidence
      </button>

      {drawerOpen && (
        <NetworkEvidenceDrawer providerId={providerId} planId={planId} locationId={locationId} onClose={() => setDrawerOpen(false)} />
      )}
    </div>
  )
}

export default NetworkEvidenceBadge
