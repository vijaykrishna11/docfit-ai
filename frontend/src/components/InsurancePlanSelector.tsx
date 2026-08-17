import { useEffect, useState } from 'react'
import { fetchPayerPlans } from '../api/client'
import type { InsurancePlanDto, PayerDto } from '../api/types'
import { InfoIcon, InsuranceIcon } from './icons'

export interface InsurancePlanSelectorValue {
  payerId: string
  planId: string
}

interface InsurancePlanSelectorProps {
  payers: PayerDto[]
  value: InsurancePlanSelectorValue
  onChange: (value: InsurancePlanSelectorValue) => void
}

/**
 * Payer -> plan selector for network evidence. Selecting a payer with no integrated plan data
 * never blocks search -- it just explains that network verification isn't available for that
 * insurer yet (CLAUDE.md 15). Plan selection is never required to search.
 */
function InsurancePlanSelector({ payers, value, onChange }: InsurancePlanSelectorProps) {
  const [plans, setPlans] = useState<InsurancePlanDto[]>([])
  const [plansLoading, setPlansLoading] = useState(false)
  const [plansUnavailable, setPlansUnavailable] = useState(false)

  const selectedPayer = payers.find((p) => String(p.id) === value.payerId)

  useEffect(() => {
    if (!selectedPayer || !selectedPayer.hasIntegratedPlans) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- clears stale plans when the payer selection changes to one with no integration
      setPlans([])
      return
    }
    let cancelled = false
    setPlansLoading(true)
    setPlansUnavailable(false)
    fetchPayerPlans(selectedPayer.id)
      .then((result) => {
        if (cancelled) return
        setPlans(result)
      })
      .catch(() => {
        if (cancelled) return
        setPlans([])
        setPlansUnavailable(true)
      })
      .finally(() => {
        if (!cancelled) setPlansLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- refetch only when the selected payer id changes
  }, [selectedPayer?.id])

  function handlePayerChange(payerId: string) {
    onChange({ payerId, planId: '' })
  }

  function handlePlanChange(planId: string) {
    onChange({ payerId: value.payerId, planId })
  }

  const showPlanSelect = Boolean(selectedPayer?.hasIntegratedPlans) && !plansUnavailable

  return (
    <div className="field">
      <label htmlFor="insurance-payer">Insurance</label>
      <div className="input-with-icon">
        <InsuranceIcon width={18} height={18} />
        <select id="insurance-payer" value={value.payerId} onChange={(event) => handlePayerChange(event.target.value)}>
          <option value="">Select payer / not sure</option>
          {payers.map((payer) => (
            <option key={payer.id} value={payer.id}>
              {payer.name}
            </option>
          ))}
        </select>
      </div>

      {selectedPayer && !selectedPayer.hasIntegratedPlans && (
        <p className="field-hint">
          <InfoIcon width={13} height={13} />
          Network verification is not currently available for this insurer. You can still search.
        </p>
      )}

      {selectedPayer && plansUnavailable && (
        <p className="field-hint">
          <InfoIcon width={13} height={13} />
          Plan list is temporarily unavailable. You can still search.
        </p>
      )}

      {showPlanSelect && (
        <div className="input-with-icon" style={{ marginTop: '0.5rem' }}>
          <select
            id="insurance-plan"
            aria-label="Plan"
            value={value.planId}
            onChange={(event) => handlePlanChange(event.target.value)}
            disabled={plansLoading}
          >
            <option value="">{plansLoading ? 'Loading plans…' : 'Select a plan (optional)'}</option>
            {plans.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.planName}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  )
}

export default InsurancePlanSelector
