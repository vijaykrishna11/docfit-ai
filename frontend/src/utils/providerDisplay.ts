export function titleCase(value: string): string {
  return value.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
}

interface NamedEntity {
  organizationName: string | null
  firstName: string | null
  lastName: string | null
}

export function providerDisplayName(provider: NamedEntity): string {
  if (provider.organizationName) {
    return titleCase(provider.organizationName)
  }
  const parts = [provider.firstName, provider.lastName].filter((part): part is string => Boolean(part))
  return parts.length > 0 ? titleCase(parts.join(' ')) : 'Unknown provider'
}

export function initialsFor(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
}

export function formatDistance(miles: number | null | undefined): string {
  if (miles == null) return ''
  return miles >= 0 && miles < 1 ? '< 1 mi' : `${miles} mi`
}

export interface AddressLike {
  addressLine1: string
  addressLine2: string | null
  city: string
  stateCode: string
  postalCode: string
}

export function formattedAddress(provider: AddressLike): { line1: string; line2: string } {
  const line1 = `${titleCase(provider.addressLine1)}${
    provider.addressLine2 ? `, ${titleCase(provider.addressLine2)}` : ''
  }`
  const line2 = `${titleCase(provider.city)}, ${provider.stateCode} ${provider.postalCode}`
  return { line1, line2 }
}

/** Public Google Maps "Maps URLs" deep link -- no API key, no paid Maps API. */
export function directionsUrl(provider: AddressLike): string {
  const query = [provider.addressLine1, provider.addressLine2, provider.city, provider.stateCode, provider.postalCode]
    .filter(Boolean)
    .join(', ')
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
}

/** Normalizes a phone number for a tel: href while leaving the original display string untouched. */
export function telHref(phone: string): string {
  const normalized = phone.replace(/[^\d+]/g, '')
  return `tel:${normalized}`
}
