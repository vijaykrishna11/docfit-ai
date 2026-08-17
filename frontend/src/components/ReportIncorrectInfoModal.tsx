import { useEffect, useRef, useState } from 'react'
import { ApiError, submitProviderDataReport } from '../api/client'
import type { ReportTypeValue } from '../api/types'
import { AlertIcon, CheckIcon, CloseIcon } from './icons'

interface ReportIncorrectInfoModalProps {
  providerId: number
  /** The office currently being shown, if any -- attached so a report about "wrong phone" or "wrong address" is unambiguous about which office it refers to. */
  locationId?: number
  onClose: () => void
}

const COMMENT_MAX_LENGTH = 1000

const REPORT_TYPE_OPTIONS: { value: ReportTypeValue; label: string }[] = [
  { value: 'WRONG_ADDRESS', label: 'Wrong address' },
  { value: 'WRONG_PHONE_NUMBER', label: 'Wrong phone number' },
  { value: 'PROVIDER_NOT_AT_LOCATION', label: 'Provider no longer at this location' },
  { value: 'NAME_APPEARS_INCORRECT', label: 'Provider name appears incorrect' },
  { value: 'SPECIALTY_APPEARS_INCORRECT', label: 'Specialty information appears incorrect' },
  { value: 'DUPLICATE_PROVIDER_OR_LOCATION', label: 'Duplicate provider/location' },
  { value: 'INSURANCE_INFO_APPEARS_INCORRECT', label: 'Insurance/network information appears incorrect' },
  { value: 'OTHER', label: 'Other directory-data issue' },
]

/**
 * Directory-data correction reporting (CLAUDE.md "Data Correction Reporting"). Deliberately asks
 * nothing clinical -- no diagnosis, reason for visit, medical history, treatment, or medication.
 * Never promises the record will definitely be corrected; reports are review signals only.
 */
function ReportIncorrectInfoModal({ providerId, locationId, onClose }: ReportIncorrectInfoModalProps) {
  const [reportType, setReportType] = useState<ReportTypeValue | ''>('')
  const [comment, setComment] = useState('')
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeButtonRef.current?.focus()
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!reportType) return
    setStatus('submitting')
    setErrorMessage(null)
    try {
      await submitProviderDataReport(providerId, {
        reportType,
        providerLocationId: locationId,
        comment: comment.trim() || undefined,
      })
      setStatus('success')
    } catch (error) {
      setStatus('error')
      setErrorMessage(error instanceof ApiError ? error.message : 'Unable to submit this report right now. Please try again.')
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-panel report-info-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-info-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <h2 id="report-info-title">Report incorrect information</h2>
          <button ref={closeButtonRef} type="button" className="modal-close" onClick={onClose} aria-label="Close">
            <CloseIcon width={16} height={16} />
          </button>
        </div>

        {status === 'success' ? (
          <div className="state-panel success-panel">
            <CheckIcon width={20} height={20} />
            <div className="state-panel-copy">
              <h3>Thanks. This report helps us review directory information.</h3>
              <p>We review reports periodically -- this does not change the provider record automatically.</p>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <p className="field-hint">
              DocFit AI uses public directory data, which can change. Reports help us identify
              records that may need review.
            </p>

            <div className="field">
              <label htmlFor="report-type">What&rsquo;s incorrect?</label>
              <select
                id="report-type"
                className="plain-select"
                required
                value={reportType}
                onChange={(event) => setReportType(event.target.value as ReportTypeValue)}
              >
                <option value="" disabled>
                  Select an issue
                </option>
                {REPORT_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="report-comment">Add details (optional)</label>
              <textarea
                id="report-comment"
                className="plain-textarea"
                rows={4}
                maxLength={COMMENT_MAX_LENGTH}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="What did you notice?"
              />
              <p className="field-hint">
                {comment.length}/{COMMENT_MAX_LENGTH}
              </p>
            </div>

            {status === 'error' && errorMessage && (
              <div className="state-panel error-panel" role="alert">
                <AlertIcon width={18} height={18} />
                <p>{errorMessage}</p>
              </div>
            )}

            <div className="modal-actions">
              <button type="button" className="secondary-button" onClick={onClose}>
                Cancel
              </button>
              <button type="submit" className="primary-button" disabled={!reportType || status === 'submitting'}>
                {status === 'submitting' ? 'Submitting…' : 'Submit report'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default ReportIncorrectInfoModal
