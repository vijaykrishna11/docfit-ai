import type { InsuranceCarrierDto, ProviderSearchResponseDto, SpecialtyDto } from './types'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`)
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status} ${response.statusText}`)
  }
  return response.json() as Promise<T>
}

export function fetchSpecialties(): Promise<SpecialtyDto[]> {
  return getJson<SpecialtyDto[]>('/api/specialties')
}

export function fetchInsuranceCarriers(): Promise<InsuranceCarrierDto[]> {
  return getJson<InsuranceCarrierDto[]>('/api/insurance-carriers')
}

export interface ProviderSearchParams {
  specialty: string
  zip: string
}

export function searchProviders({ specialty, zip }: ProviderSearchParams): Promise<ProviderSearchResponseDto> {
  const params = new URLSearchParams({
    specialty,
    zip,
    radius: '25',
    size: '20',
  })
  return getJson<ProviderSearchResponseDto>(`/api/providers/search?${params.toString()}`)
}
