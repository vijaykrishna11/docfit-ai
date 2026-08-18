import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchSpecialties, resolveApiBaseUrl, searchProviders, setAccessToken, setRefreshHandler } from './client'

describe('resolveApiBaseUrl', () => {
  it('defaults to the local backend dev server in development', () => {
    expect(resolveApiBaseUrl(undefined, false, 'https://docfit-ai.onrender.com')).toBe('http://localhost:8080')
  })

  it('defaults to the current same-origin location in a production build', () => {
    // Render same-origin deployment (CLAUDE.md): the production build must never hardcode a
    // specific deployed hostname -- it calls the API at whatever origin is actually serving it.
    expect(resolveApiBaseUrl(undefined, true, 'https://docfit-ai.onrender.com')).toBe('https://docfit-ai.onrender.com')
  })

  it('an explicit VITE_API_BASE_URL always wins, in dev or production', () => {
    expect(resolveApiBaseUrl('https://api.docfit.example', false, 'http://localhost:5173')).toBe('https://api.docfit.example')
    expect(resolveApiBaseUrl('https://api.docfit.example', true, 'https://docfit-ai.onrender.com')).toBe(
      'https://api.docfit.example',
    )
  })
})

describe('searchProviders', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('builds a request with location, radius, sort, and page -- and never leaks undefined values', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ results: [], page: 0, size: 20, totalElements: 0, totalPages: 0, originLabel: 'Long Beach, CA' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await searchProviders({ specialty: 'CARDIOLOGY', location: '90815', radius: 50, sort: 'distance', page: 0 })

    const url = new URL(fetchMock.mock.calls[0][0] as string)
    expect(url.searchParams.get('specialty')).toBe('CARDIOLOGY')
    expect(url.searchParams.get('location')).toBe('90815')
    expect(url.searchParams.get('radius')).toBe('50')
    expect(url.searchParams.get('sort')).toBe('distance')
    expect(url.searchParams.get('page')).toBe('0')
    expect(url.searchParams.toString()).not.toContain('undefined')
    expect(url.searchParams.has('zip')).toBe(false)
    expect(url.searchParams.has('insurance')).toBe(false)
  })

  it('sends lat/lng instead of location when coordinates are provided', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ results: [], page: 0, size: 20, totalElements: 0, totalPages: 0, originLabel: null }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await searchProviders({ specialty: 'CARDIOLOGY', lat: 33.77, lng: -118.19, radius: 25, sort: 'distance', page: 0 })

    const url = new URL(fetchMock.mock.calls[0][0] as string)
    expect(url.searchParams.get('lat')).toBe('33.77')
    expect(url.searchParams.get('lng')).toBe('-118.19')
    expect(url.searchParams.has('location')).toBe(false)
  })

  it('maps an HTTP 400 to a clear invalid-search message instead of a raw error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400, statusText: 'Bad Request' }))

    await expect(
      searchProviders({ specialty: 'CARDIOLOGY', location: 'Nowhere', radius: 25, sort: 'distance', page: 0 }),
    ).rejects.toThrow(/couldn.t process this search/i)
  })

  it('maps a network failure to an unreachable-service message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(
      searchProviders({ specialty: 'CARDIOLOGY', location: '90815', radius: 25, sort: 'distance', page: 0 }),
    ).rejects.toThrow(/unable to reach the search service/i)
  })
})

describe('401 refresh-and-retry', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setRefreshHandler(null)
    setAccessToken(null)
  })

  it('shares one in-flight refresh across concurrent 401s instead of firing one per request', async () => {
    // The refresh token is single-use/rotating server-side -- two independent refresh calls
    // fired from two requests that both hit a 401 at once would race against the same cookie.
    let fetchCallCount = 0
    const fetchMock = vi.fn().mockImplementation(async () => {
      fetchCallCount += 1
      // Calls 1-2 are each request's initial (expired-token) attempt; everything after is the
      // post-refresh retry.
      if (fetchCallCount <= 2) {
        return { ok: false, status: 401, json: async () => ({}) }
      }
      return { ok: true, status: 200, json: async () => [] }
    })
    vi.stubGlobal('fetch', fetchMock)

    let refreshCallCount = 0
    setRefreshHandler(async () => {
      refreshCallCount += 1
      await Promise.resolve()
      return 'new-access-token'
    })

    await Promise.all([fetchSpecialties(), fetchSpecialties()])

    expect(refreshCallCount).toBe(1)
    expect(fetchCallCount).toBe(4)
  })
})
