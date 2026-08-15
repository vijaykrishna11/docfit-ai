import { useNavigate } from 'react-router-dom'
import { useCompare } from '../context/CompareContext'

function CompareBar() {
  const { selectedIds, clear } = useCompare()
  const navigate = useNavigate()

  if (selectedIds.length < 2) {
    return null
  }

  return (
    <div className="compare-bar" role="region" aria-label="Provider comparison">
      <p>
        {selectedIds.length} provider{selectedIds.length === 1 ? '' : 's'} selected to compare
      </p>
      <div className="compare-bar-actions">
        <button type="button" className="secondary-button" onClick={clear}>
          Clear
        </button>
        <button
          type="button"
          className="primary-button"
          onClick={() => navigate(`/compare?ids=${selectedIds.join(',')}`)}
        >
          Compare {selectedIds.length} providers
        </button>
      </div>
    </div>
  )
}

export default CompareBar
