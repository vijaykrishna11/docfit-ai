import { useEffect, useState } from 'react'
import { ApiError, fetchVerificationItems, updateVerificationItem } from '../api/client'
import {
  VERIFICATION_TYPE_LABELS,
  type VerificationItemDto,
  type VerificationItemStatusValue,
  type VerificationTypeValue,
} from '../api/types'
import { useToast } from '../context/ToastContext'
import { CheckIcon } from './icons'

const VERIFICATION_TYPES = Object.keys(VERIFICATION_TYPE_LABELS) as VerificationTypeValue[]

const STATUS_LABELS: Record<VerificationItemStatusValue, string> = {
  NOT_STARTED: 'Not started',
  NEEDS_CONFIRMATION: 'Needs confirmation',
  CONFIRMED_BY_USER: 'Marked confirmed by you',
  NOT_APPLICABLE: 'Not applicable',
}

const STATUS_VALUES: VerificationItemStatusValue[] = ['NOT_STARTED', 'NEEDS_CONFIRMATION', 'CONFIRMED_BY_USER', 'NOT_APPLICABLE']

/**
 * "Before you contact this provider" checklist. For a signed-in user, state persists
 * (CLAUDE.md "Provider Detail Checklist"). CONFIRMED_BY_USER means only that the user says they
 * confirmed it -- never promoted into DocFit's own data (CLAUDE.md "User Confirmation Semantics").
 */
function VerificationChecklist({
  providerId,
  isAuthenticated,
  onProgressChange,
}: {
  providerId: number
  isAuthenticated: boolean
  onProgressChange?: (completed: number, total: number) => void
}) {
  const { showToast } = useToast()
  const [items, setItems] = useState<Record<VerificationTypeValue, VerificationItemStatusValue>>(
    () => Object.fromEntries(VERIFICATION_TYPES.map((type) => [type, 'NOT_STARTED'])) as Record<
      VerificationTypeValue,
      VerificationItemStatusValue
    >,
  )
  const [savingType, setSavingType] = useState<VerificationTypeValue | null>(null)

  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    fetchVerificationItems(providerId)
      .then((results: VerificationItemDto[]) => {
        if (cancelled) return
        setItems(Object.fromEntries(results.map((item) => [item.verificationType, item.status])) as Record<
          VerificationTypeValue,
          VerificationItemStatusValue
        >)
      })
      .catch(() => {
        // Non-fatal: checklist simply stays at defaults until the next successful fetch.
      })
    return () => {
      cancelled = true
    }
  }, [providerId, isAuthenticated])

  useEffect(() => {
    if (!onProgressChange) return
    const completed = VERIFICATION_TYPES.filter(
      (type) => items[type] === 'CONFIRMED_BY_USER' || items[type] === 'NOT_APPLICABLE',
    ).length
    onProgressChange(completed, VERIFICATION_TYPES.length)
  }, [items, onProgressChange])

  async function handleChange(type: VerificationTypeValue, status: VerificationItemStatusValue) {
    const previous = items[type]
    setItems((current) => ({ ...current, [type]: status }))
    setSavingType(type)
    try {
      await updateVerificationItem(providerId, type, status)
      showToast('Checklist updated')
    } catch (error) {
      setItems((current) => ({ ...current, [type]: previous }))
      showToast(error instanceof ApiError ? error.message : 'Could not update checklist. Please try again.')
    } finally {
      setSavingType(null)
    }
  }

  return (
    <section className="verification-checklist">
      <h2>Before you contact this provider</h2>
      <p className="results-subtext">
        These are reminders to verify information yourself -- DocFit AI doesn&rsquo;t assert that any of them are
        true.
      </p>
      <ul>
        {VERIFICATION_TYPES.map((type) => {
          const status = items[type]
          const resolved = status === 'CONFIRMED_BY_USER' || status === 'NOT_APPLICABLE'
          return (
            <li key={type} className="verification-checklist-item">
              <span className={`verification-checklist-mark${resolved ? ' is-resolved' : ''}`} aria-hidden="true">
                {resolved && <CheckIcon width={12} height={12} />}
              </span>
              <span className="verification-checklist-label">{VERIFICATION_TYPE_LABELS[type]}</span>
              {isAuthenticated ? (
                <label className="verification-checklist-status">
                  <span className="visually-hidden">Status for: {VERIFICATION_TYPE_LABELS[type]}</span>
                  <select
                    value={status}
                    disabled={savingType === type}
                    onChange={(event) => void handleChange(type, event.target.value as VerificationItemStatusValue)}
                  >
                    {STATUS_VALUES.map((value) => (
                      <option key={value} value={value}>
                        {STATUS_LABELS[value]}
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <span className="verification-checklist-status-static">Sign in to track</span>
              )}
            </li>
          )
        })}
      </ul>
      {!isAuthenticated && (
        <p className="field-hint">Sign in to keep track of what you&rsquo;ve confirmed for this provider.</p>
      )}
    </section>
  )
}

export default VerificationChecklist
