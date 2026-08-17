import { useState } from 'react'
import { ApiError, updateNavigationStatus } from '../api/client'
import { NAVIGATION_STATUS_LABELS, NAVIGATION_STATUS_VALUES, type NavigationStatusValue } from '../api/types'
import { useToast } from '../context/ToastContext'

/** A fixed, allowlisted administrative status -- never "recommended"/"approved" (CLAUDE.md "Status UX"). */
function NavigationStatusSelect({
  providerId,
  status,
  onChanged,
}: {
  providerId: number
  status: NavigationStatusValue
  onChanged: (status: NavigationStatusValue) => void
}) {
  const { showToast } = useToast()
  const [isSaving, setIsSaving] = useState(false)

  async function handleChange(next: NavigationStatusValue) {
    setIsSaving(true)
    try {
      await updateNavigationStatus(providerId, next)
      onChanged(next)
      showToast('Status updated')
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : 'Could not update status. Please try again.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <span className="navigation-status-select">
      <select
        aria-label="Navigation status"
        value={status}
        disabled={isSaving}
        onChange={(event) => void handleChange(event.target.value as NavigationStatusValue)}
      >
        {NAVIGATION_STATUS_VALUES.map((value) => (
          <option key={value} value={value}>
            {NAVIGATION_STATUS_LABELS[value]}
          </option>
        ))}
      </select>
    </span>
  )
}

export default NavigationStatusSelect
