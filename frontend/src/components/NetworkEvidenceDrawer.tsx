import { useEffect, useRef, useState } from 'react'
import { ApiError, fetchProviderNetworkEvidence } from '../api/client'
import type { NetworkEvidenceDetailDto } from '../api/types'
import { AlertIcon, BadgeIcon, CheckIcon, CloseIcon, InfoIcon } from './icons'
import { evidenceStatusCopy, formatCheckedAt, freshnessCopy, matchMethodCopy } from '../utils/networkEvidenceDisplay'

interface NetworkEvidenceDrawerProps {
  providerId: number
  planId: number
  onClose: () => void
}

/**
 * "View network evidence" panel (CLAUDE.md 33). Accessible modal: focus moves in on open,
 * Escape closes, close button is reachable by keyboard. Never renders "covered" or
 * "in network" -- only what DocFit AI actually knows, sourced and dated.
 */
function NetworkEvidenceDrawer({ providerId, planId, onClose }: NetworkEvidenceDrawerProps) {
  const [detail, setDetail] = useState<NetworkEvidenceDetailDto | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeButtonRef.current?.focus()
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  useEffect(() => {
    let cancelled = false
    fetchProviderNetworkEvidence(providerId, planId)
      .then((result) => {
        if (!cancelled) setDetail(result)
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setErrorMessage(error instanceof ApiError ? error.message : 'Network evidence is temporarily unavailable.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [providerId, planId])

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-panel network-evidence-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="network-evidence-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <h2 id="network-evidence-title">Network evidence</h2>
          <button ref={closeButtonRef} type="button" className="modal-close" onClick={onClose} aria-label="Close network evidence">
            <CloseIcon width={16} height={16} />
          </button>
        </div>

        {loading && <p className="results-heading-loading">Loading network evidence…</p>}

        {!loading && errorMessage && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={20} height={20} />
            <p>{errorMessage}</p>
          </div>
        )}

        {!loading && detail && (
          <>
            <dl className="network-evidence-grid">
              <div>
                <dt>Plan / network</dt>
                <dd>
                  {detail.planName}
                  {detail.networkName ? ` — ${detail.networkName}` : ''}
                </dd>
              </div>
              <div>
                <dt>Payer</dt>
                <dd>{detail.payerName}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>
                  <span className={`evidence-status evidence-status-${detail.status.toLowerCase()}`}>
                    {evidenceStatusCopy(detail.status)}
                  </span>
                  {detail.freshness && <span className="evidence-freshness"> · {freshnessCopy(detail.freshness)}</span>}
                </dd>
              </div>
              {(detail.matchedAddressLine1 || detail.matchedCity) && (
                <div>
                  <dt>Location matched</dt>
                  <dd>
                    {detail.matchedAddressLine1}
                    {detail.matchedAddressLine1 && <br />}
                    {[detail.matchedCity, detail.matchedStateCode, detail.matchedPostalCode].filter(Boolean).join(', ')}
                  </dd>
                </div>
              )}
              {detail.matchMethod && (
                <div>
                  <dt>Matched by</dt>
                  <dd>{matchMethodCopy(detail.matchMethod)}</dd>
                </div>
              )}
              {detail.sourceName && (
                <div>
                  <dt>Source</dt>
                  <dd>
                    <BadgeIcon width={13} height={13} /> {detail.sourceName}
                    {detail.synthetic && <span className="chip chip-demo">Synthetic demo data</span>}
                  </dd>
                </div>
              )}
              {detail.checkedAt && (
                <div>
                  <dt>Checked</dt>
                  <dd>{formatCheckedAt(detail.checkedAt)}</dd>
                </div>
              )}
            </dl>

            <div className="disclaimer-panel">
              <InfoIcon width={16} height={16} />
              <ul>
                {detail.limitations.map((limitation) => (
                  <li key={limitation}>{limitation}</li>
                ))}
              </ul>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export function NetworkEvidenceStatusIcon({ status }: { status: string }) {
  if (status === 'EVIDENCE_FOUND') return <CheckIcon width={13} height={13} />
  return <InfoIcon width={13} height={13} />
}

export default NetworkEvidenceDrawer
