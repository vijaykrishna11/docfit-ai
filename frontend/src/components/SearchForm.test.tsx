import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SearchForm from './SearchForm'

const SPECIALTIES = [{ code: 'CARDIOLOGY', name: 'Cardiology' }]

describe('SearchForm', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const PAYERS = [{ id: 1, code: 'AETNA', name: 'Aetna', hasIntegratedPlans: false }]

  it('renders the specialty/insurance dropdowns, location input, radius selector, and CTA', () => {
    render(<SearchForm specialties={SPECIALTIES} payers={PAYERS} onSearch={vi.fn()} />)

    expect(screen.getByLabelText(/specialty/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/insurance/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/location/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/radius/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /find providers/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Cardiology' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Aetna' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '25 miles' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '50 miles' })).toBeInTheDocument()
  })

  it('shows a non-blocking message when the selected payer has no integrated plan data', () => {
    render(<SearchForm specialties={SPECIALTIES} payers={PAYERS} onSearch={vi.fn()} />)

    fireEvent.change(screen.getByLabelText(/insurance/i), { target: { value: '1' } })

    expect(screen.getByText(/network verification is not currently available/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /find providers/i })).not.toBeDisabled()
  })

  it('submits the typed location and selected radius', () => {
    const onSearch = vi.fn()
    render(<SearchForm specialties={SPECIALTIES} payers={[]} onSearch={onSearch} />)

    fireEvent.change(screen.getByLabelText(/specialty/i), { target: { value: 'CARDIOLOGY' } })
    fireEvent.change(screen.getByLabelText(/location/i), { target: { value: '90815' } })
    fireEvent.change(screen.getByLabelText(/radius/i), { target: { value: '50' } })
    fireEvent.click(screen.getByRole('button', { name: /find providers/i }))

    expect(onSearch).toHaveBeenCalledWith(
      expect.objectContaining({ specialty: 'CARDIOLOGY', location: '90815', radius: 50 }),
    )
  })

  it('uses geolocation coordinates once "Use my location" succeeds', async () => {
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      success({ coords: { latitude: 33.77, longitude: -118.19 } } as GeolocationPosition)
    })
    vi.stubGlobal('navigator', { ...globalThis.navigator, geolocation: { getCurrentPosition } })

    const onSearch = vi.fn()
    render(<SearchForm specialties={SPECIALTIES} payers={[]} onSearch={onSearch} />)

    fireEvent.click(screen.getByRole('button', { name: /use my location/i }))
    await waitFor(() => expect(screen.getByDisplayValue(/current location/i)).toBeInTheDocument())

    fireEvent.change(screen.getByLabelText(/specialty/i), { target: { value: 'CARDIOLOGY' } })
    fireEvent.click(screen.getByRole('button', { name: /find providers/i }))

    expect(onSearch).toHaveBeenCalledWith(
      expect.objectContaining({ specialty: 'CARDIOLOGY', lat: 33.77, lng: -118.19 }),
    )
  })

  it('shows a helpful message when geolocation permission is denied', async () => {
    const getCurrentPosition = vi.fn((_success: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError)
    })
    vi.stubGlobal('navigator', { ...globalThis.navigator, geolocation: { getCurrentPosition } })

    render(<SearchForm specialties={SPECIALTIES} payers={[]} onSearch={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: /use my location/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/denied/i))
  })
})
