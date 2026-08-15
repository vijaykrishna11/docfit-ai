import { Link } from 'react-router-dom'
import type { ProviderSearchResultDto } from '../api/types'
import { useCompare } from '../context/CompareContext'
import {
  directionsUrl,
  formatDistance,
  formattedAddress,
  initialsFor,
  providerDisplayName,
  telHref,
} from '../utils/providerDisplay'
import { BadgeIcon, DirectionsIcon, LocationIcon, PhoneIcon } from './icons'

function ProviderCard({ provider }: { provider: ProviderSearchResultDto }) {
  const { isSelected, toggle, isFull } = useCompare()
  const name = providerDisplayName(provider)
  const { line1, line2 } = formattedAddress(provider)
  const selected = isSelected(provider.id)
  const disableCompareToggle = !selected && isFull

  return (
    <li className="provider-card">
      <div className="provider-card-top">
        <div className="avatar" aria-hidden="true">
          {initialsFor(name)}
        </div>
        <div className="provider-card-heading">
          <h3>{name}</h3>
          <span className="specialty-badge">{provider.specialtyDisplayName}</span>
        </div>
        <span className="distance-badge">
          <LocationIcon width={13} height={13} />
          {formatDistance(provider.distanceMiles)}
        </span>
      </div>

      <div className="provider-card-details">
        <p className="detail">
          <LocationIcon width={16} height={16} />
          <span>
            {line1}
            <br />
            {line2}
          </span>
        </p>
        {provider.phone && (
          <p className="detail">
            <PhoneIcon width={16} height={16} />
            <span>{provider.phone}</span>
          </p>
        )}
        <p className="npi">
          <BadgeIcon width={14} height={14} />
          <span>NPI {provider.npiNumber}</span>
        </p>
      </div>

      <div className="provider-card-actions">
        <label className="compare-checkbox">
          <input type="checkbox" checked={selected} disabled={disableCompareToggle} onChange={() => toggle(provider.id)} />
          Compare
        </label>

        <div className="provider-card-buttons">
          {provider.phone && (
            <a className="ghost-button" href={telHref(provider.phone)}>
              <PhoneIcon width={14} height={14} />
              Call
            </a>
          )}
          <a className="ghost-button" href={directionsUrl(provider)} target="_blank" rel="noopener noreferrer">
            <DirectionsIcon width={14} height={14} />
            Directions
          </a>
          <Link className="ghost-button" to={`/providers/${provider.id}`}>
            View details
          </Link>
        </div>
      </div>
    </li>
  )
}

export default ProviderCard
