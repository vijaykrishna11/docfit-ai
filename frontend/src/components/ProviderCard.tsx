import type { ProviderSearchResultDto } from '../api/types'
import { BadgeIcon, LocationIcon, PhoneIcon } from './icons'

function titleCase(value: string): string {
  return value.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
}

function providerName(provider: ProviderSearchResultDto): string {
  if (provider.organizationName) {
    return titleCase(provider.organizationName)
  }
  const parts = [provider.firstName, provider.lastName].filter((part): part is string => Boolean(part))
  return parts.length > 0 ? titleCase(parts.join(' ')) : 'Unknown provider'
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
}

function formatDistance(miles: number): string {
  return miles >= 0 && miles < 1 ? '< 1 mi' : `${miles} mi`
}

function ProviderCard({ provider }: { provider: ProviderSearchResultDto }) {
  const name = providerName(provider)
  const address = `${titleCase(provider.addressLine1)}${
    provider.addressLine2 ? `, ${titleCase(provider.addressLine2)}` : ''
  }`
  const cityLine = `${titleCase(provider.city)}, ${provider.stateCode} ${provider.postalCode}`

  return (
    <li className="provider-card">
      <div className="provider-card-top">
        <div className="avatar" aria-hidden="true">
          {initials(name)}
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
            {address}
            <br />
            {cityLine}
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
    </li>
  )
}

export default ProviderCard
