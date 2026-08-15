import type { ProviderSearchResultDto } from '../api/types'

function providerName(provider: ProviderSearchResultDto): string {
  if (provider.organizationName) {
    return provider.organizationName
  }
  const parts = [provider.firstName, provider.lastName].filter(Boolean)
  return parts.length > 0 ? parts.join(' ') : 'Unknown provider'
}

function ProviderCard({ provider }: { provider: ProviderSearchResultDto }) {
  return (
    <li className="provider-card">
      <div className="provider-card-header">
        <h3>{providerName(provider)}</h3>
        <span className="distance">{provider.distanceMiles} mi</span>
      </div>
      <p className="specialty">{provider.specialtyDisplayName}</p>
      <p className="address">
        {provider.addressLine1}
        {provider.addressLine2 ? `, ${provider.addressLine2}` : ''}
        <br />
        {provider.city}, {provider.stateCode} {provider.postalCode}
      </p>
      {provider.phone && <p className="phone">{provider.phone}</p>}
      <p className="npi">NPI: {provider.npiNumber}</p>
    </li>
  )
}

export default ProviderCard
