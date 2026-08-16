import type { CSSProperties } from 'react'
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
import { BadgeIcon, BuildingIcon, CheckIcon, DirectionsIcon, LocationIcon, PhoneIcon } from './icons'
import NetworkEvidenceBadge from './NetworkEvidenceBadge'
import SaveProviderButton from './SaveProviderButton'
import WhyThisResult from './WhyThisResult'

const MAX_STAGGER_INDEX = 8
const STAGGER_STEP_MS = 35

interface ProviderCardProps {
  provider: ProviderSearchResultDto
  entranceIndex?: number
  originLabel?: string | null
  planId?: number
  /** Map/list sync (CLAUDE.md "Map Marker Interaction"): clicking a card highlights its map marker. Optional -- only wired when a map is actually showing. */
  onSelect?: () => void
  isSelected?: boolean
}

function ProviderCard({ provider, entranceIndex = 0, originLabel, planId, onSelect, isSelected: isMapSelected }: ProviderCardProps) {
  const { isSelected: isCompareSelected, toggle, isFull } = useCompare()
  const name = providerDisplayName(provider)
  const { line1, line2 } = formattedAddress(provider.location)
  const selected = isCompareSelected(provider.id)
  const disableCompareToggle = !selected && isFull
  const isOrganization = provider.entityType === 'ORGANIZATION'
  const style = {
    '--entrance-delay': `${Math.min(entranceIndex, MAX_STAGGER_INDEX) * STAGGER_STEP_MS}ms`,
  } as CSSProperties

  return (
    <li
      className={`provider-card${selected ? ' is-selected' : ''}${isMapSelected ? ' is-map-selected' : ''}`}
      style={style}
      onClick={onSelect}
    >
      <div className="provider-card-top">
        <div className={`avatar${isOrganization ? ' avatar-organization' : ''}`} aria-hidden="true">
          {isOrganization ? <BuildingIcon width={18} height={18} /> : initialsFor(name)}
        </div>
        <div className="provider-card-heading">
          <h3>{name}</h3>
          <div className="provider-card-heading-badges">
            <span className="specialty-badge">{provider.specialtyDisplayName}</span>
            {provider.locationCount > 1 && <span className="chip multi-location-chip">{provider.locationCount} practice locations</span>}
          </div>
        </div>
        <div className="provider-card-top-actions">
          <span className="distance-badge">
            <LocationIcon width={13} height={13} />
            {formatDistance(provider.distanceMiles)}
          </span>
          <SaveProviderButton providerId={provider.id} />
        </div>
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
        {provider.location.phone && (
          <p className="detail">
            <PhoneIcon width={16} height={16} />
            <span>{provider.location.phone}</span>
          </p>
        )}
        <p className="npi">
          <BadgeIcon width={14} height={14} />
          <span>NPI {provider.npiNumber}</span>
        </p>
        {planId != null && provider.networkEvidence && (
          <NetworkEvidenceBadge
            providerId={provider.id}
            planId={planId}
            locationId={provider.location.id}
            evidence={provider.networkEvidence}
          />
        )}
        <WhyThisResult
          specialtyDisplayName={provider.specialtyDisplayName}
          npiNumber={provider.npiNumber}
          distanceMiles={provider.distanceMiles}
          originLabel={originLabel}
          networkEvidence={provider.networkEvidence}
          coordinatePrecision={provider.location.coordinatePrecision}
          hasPhone={Boolean(provider.location.phone)}
        />
      </div>

      <div className="provider-card-actions">
        <label className={`compare-checkbox${selected ? ' is-checked' : ''}`}>
          <input
            type="checkbox"
            checked={selected}
            disabled={disableCompareToggle}
            onChange={() => toggle(provider.id)}
          />
          <span className="compare-checkbox-box" aria-hidden="true">
            {selected && <CheckIcon width={11} height={11} />}
          </span>
          Compare
        </label>

        <div className="provider-card-buttons">
          {provider.location.phone && (
            <a className="ghost-button" href={telHref(provider.location.phone)}>
              <PhoneIcon width={14} height={14} />
              Call
            </a>
          )}
          <a className="ghost-button" href={directionsUrl(provider.location)} target="_blank" rel="noopener noreferrer">
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
