import { useCallback, useState } from 'react'

export type GeolocationStatus = 'idle' | 'requesting' | 'granted' | 'denied' | 'unsupported' | 'error'

export interface GeolocationCoords {
  lat: number
  lng: number
}

interface GeolocationState {
  status: GeolocationStatus
  coords: GeolocationCoords | null
  errorMessage: string | null
}

const UNSUPPORTED_MESSAGE = "Your browser doesn't support location detection. Enter a ZIP code or city instead."
const DENIED_MESSAGE = 'Location access was denied. Enter a ZIP code or city instead.'
const TIMEOUT_MESSAGE = "Finding your location timed out. Enter a ZIP code or city instead."
const UNKNOWN_ERROR_MESSAGE = "We couldn't determine your location. Enter a ZIP code or city instead."

export function useGeolocation() {
  const [state, setState] = useState<GeolocationState>({ status: 'idle', coords: null, errorMessage: null })

  const request = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setState({ status: 'unsupported', coords: null, errorMessage: UNSUPPORTED_MESSAGE })
      return
    }

    setState({ status: 'requesting', coords: null, errorMessage: null })

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setState({
          status: 'granted',
          coords: { lat: position.coords.latitude, lng: position.coords.longitude },
          errorMessage: null,
        })
      },
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          setState({ status: 'denied', coords: null, errorMessage: DENIED_MESSAGE })
        } else if (error.code === error.TIMEOUT) {
          setState({ status: 'error', coords: null, errorMessage: TIMEOUT_MESSAGE })
        } else {
          setState({ status: 'error', coords: null, errorMessage: UNKNOWN_ERROR_MESSAGE })
        }
      },
      { timeout: 10000, maximumAge: 300000 },
    )
  }, [])

  const reset = useCallback(() => {
    setState({ status: 'idle', coords: null, errorMessage: null })
  }, [])

  return { ...state, request, reset }
}
