import { useEffect, useState } from 'react'
import { ApiError, fetchPayers, removeSavedPlan, saveSavedPlan } from '../api/client'
import type { PayerDto, SavedPlanDto } from '../api/types'
import { useToast } from '../context/ToastContext'
import InsurancePlanSelector, { type InsurancePlanSelectorValue } from './InsurancePlanSelector'
import { InsuranceIcon } from './icons'

/**
 * Explicit opt-in only (CLAUDE.md "Saved Insurance Plan"): nothing here ever auto-saves a plan.
 * Stores only DocFit's own public payer/plan record, never a member ID or other personal
 * insurance detail (CLAUDE.md "Do Not Store Member Information").
 */
function SavedPlanCard({ savedPlan, onChanged }: { savedPlan: SavedPlanDto | null; onChanged: (plan: SavedPlanDto | null) => void }) {
  const { showToast } = useToast()
  const [editing, setEditing] = useState(false)
  const [payers, setPayers] = useState<PayerDto[]>([])
  const [selection, setSelection] = useState<InsurancePlanSelectorValue>({ payerId: '', planId: '' })
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!editing) return
    fetchPayers()
      .then(setPayers)
      .catch(() => setPayers([]))
  }, [editing])

  async function handleSave() {
    if (!selection.planId) return
    setIsSaving(true)
    setError(null)
    try {
      const plan = await saveSavedPlan(Number(selection.planId))
      onChanged(plan)
      setEditing(false)
      showToast('Plan saved')
    } catch (saveError) {
      setError(saveError instanceof ApiError ? saveError.message : 'Could not save your plan. Please try again.')
    } finally {
      setIsSaving(false)
    }
  }

  async function handleRemove() {
    try {
      await removeSavedPlan()
      onChanged(null)
      showToast('Plan removed')
    } catch (removeError) {
      showToast(removeError instanceof ApiError ? removeError.message : 'Could not remove your plan. Please try again.')
    }
  }

  return (
    <section className="navigator-panel saved-plan-card">
      <h2>
        <InsuranceIcon width={18} height={18} />
        Your saved plan
      </h2>
      <p className="results-subtext">
        Saving a plan helps DocFit show available directory evidence. It does not verify your benefits, and DocFit
        never stores your member ID or policy number.
      </p>

      {!editing && savedPlan && (
        <div className="saved-plan-current">
          <p>
            <strong>{savedPlan.payerName}</strong>
            <br />
            {savedPlan.planName}
          </p>
          <div className="provider-card-buttons">
            <button type="button" className="ghost-button" onClick={() => setEditing(true)}>
              Change
            </button>
            <button type="button" className="ghost-button" onClick={() => void handleRemove()}>
              Remove
            </button>
          </div>
        </div>
      )}

      {!editing && !savedPlan && (
        <button type="button" className="secondary-button" onClick={() => setEditing(true)}>
          Save my plan
        </button>
      )}

      {editing && (
        <div className="saved-plan-editor">
          <InsurancePlanSelector payers={payers} value={selection} onChange={setSelection} />
          {error && <p className="field-hint">{error}</p>}
          <div className="provider-card-buttons">
            <button type="button" className="ghost-button" onClick={() => setEditing(false)}>
              Cancel
            </button>
            <button type="button" className="secondary-button" disabled={!selection.planId || isSaving} onClick={() => void handleSave()}>
              {isSaving && <span className="spinner spinner-sm" aria-hidden="true" />}
              Save my plan
            </button>
          </div>
        </div>
      )}
    </section>
  )
}

export default SavedPlanCard
