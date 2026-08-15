import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, fetchProviderDetail } from '../api/client'
import type { ProviderDetailDto } from '../api/types'
import Footer from '../components/Footer'
import Header from '../components/Header'
import {
  AlertIcon,
  BadgeIcon,
  CheckIcon,
  ChevronLeftIcon,
  DirectionsIcon,
  InfoIcon,
  LocationIcon,
  PhoneIcon,
  ShareIcon,
} from '../components/icons'
import SaveProviderButton from '../components/SaveProviderButton'
import {
  directionsUrl,
  formatDistance,
  formattedAddress,
  initialsFor,
  providerDisplayName,
  telHref,
} from '../utils/providerDisplay'

type DetailStatus = 'loading' | 'success' | 'error' | 'not-found'

const UNREACHABLE_MESSAGE = 'Unable to reach the search service. Please try again.'

function ProviderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const [detail, setDetail] = useState<ProviderDetailDto | null>(null)
  const [status, setStatus] = useState<DetailStatus>('loading')
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect -- standard fetch-on-param-change pattern.
    setStatus('loading')

    const location = searchParams.get('location')
    const lat = searchParams.get('lat')
    const lng = searchParams.get('lng')

    fetchProviderDetail(Number(id), {
      location: lat && lng ? undefined : (location ?? undefined),
      lat: lat ? Number(lat) : undefined,
      lng: lng ? Number(lng) : undefined,
    })
      .then((result) => {
        if (cancelled) return
        setDetail(result)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 404) {
          setStatus('not-found')
        } else {
          setStatus('error')
          setErrorMessage(error instanceof ApiError ? error.message : UNREACHABLE_MESSAGE)
        }
      })

    return () => {
      cancelled = true
    }
  }, [id, searchParams])

  return (
    <div className="page">
      <Header />
      <main className="container detail-content">
        <button type="button" className="back-link" onClick={() => navigate(-1)}>
          <ChevronLeftIcon width={16} height={16} />
          Back to results
        </button>

        {status === 'loading' && <p className="results-heading-loading">Loading provider details…</p>}

        {status === 'not-found' && (
          <div className="state-panel empty-panel">
            <h3>Provider not found</h3>
            <p>We couldn&rsquo;t find a provider with that ID in our current demo dataset.</p>
          </div>
        )}

        {status === 'error' && (
          <div className="state-panel error-panel" role="alert">
            <AlertIcon width={22} height={22} />
            <div className="state-panel-copy">
              <h3>We couldn&rsquo;t load this provider</h3>
              <p>{errorMessage}</p>
            </div>
          </div>
        )}

        {status === 'success' && detail && <ProviderDetailCard detail={detail} />}
      </main>
      <Footer />
    </div>
  )
}

function ProviderDetailCard({ detail }: { detail: ProviderDetailDto }) {
  const name = providerDisplayName(detail)
  const { line1, line2 } = formattedAddress(detail)
  const primaryTaxonomy = detail.taxonomies.find((taxonomy) => taxonomy.primaryTaxonomy) ?? detail.taxonomies[0]
  const [shareCopied, setShareCopied] = useState(false)

  async function handleShare() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setShareCopied(true)
      window.setTimeout(() => setShareCopied(false), 2500)
    } catch {
      // Clipboard access can be denied by the browser -- fail quietly rather than show a raw error.
    }
  }

  return (
    <article className="provider-detail-card">
      <div className="provider-detail-header">
        <div className="avatar avatar-lg" aria-hidden="true">
          {initialsFor(name)}
        </div>
        <div>
          <h1>{name}</h1>
          <div className="provider-detail-badges">
            {primaryTaxonomy && <span className="specialty-badge">{primaryTaxonomy.displayName}</span>}
            {detail.distanceMiles != null && (
              <span className="distance-badge">
                <LocationIcon width={13} height={13} />
                {formatDistance(detail.distanceMiles)} from your search
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="provider-detail-actions">
        {detail.phone && (
          <a className="primary-button" href={telHref(detail.phone)}>
            <PhoneIcon width={16} height={16} />
            Call {detail.phone}
          </a>
        )}
        <a className="secondary-button" href={directionsUrl(detail)} target="_blank" rel="noopener noreferrer">
          <DirectionsIcon width={16} height={16} />
          Get directions
        </a>
        <SaveProviderButton providerId={detail.id} variant="labeled" />
        <button type="button" className={`secondary-button${shareCopied ? ' is-success' : ''}`} onClick={handleShare}>
          {shareCopied ? (
            <>
              <CheckIcon width={16} height={16} />
              Link copied
            </>
          ) : (
            <>
              <ShareIcon width={16} height={16} />
              Share
            </>
          )}
        </button>
      </div>

      <dl className="provider-detail-grid">
        <div>
          <dt>
            <LocationIcon width={13} height={13} />
            Practice address
          </dt>
          <dd>
            {line1}
            <br />
            {line2}
          </dd>
        </div>
        <div>
          <dt>
            <BadgeIcon width={13} height={13} />
            NPI
          </dt>
          <dd>{detail.npiNumber}</dd>
        </div>
        {detail.phone && (
          <div>
            <dt>
              <PhoneIcon width={13} height={13} />
              Phone
            </dt>
            <dd>{detail.phone}</dd>
          </div>
        )}
      </dl>

      <section className="provider-taxonomies">
        <h2>Taxonomy information</h2>
        <ul>
          {detail.taxonomies.map((taxonomy) => (
            <li key={taxonomy.taxonomyCode}>
              <span className="specialty-badge">{taxonomy.displayName}</span>
              {taxonomy.primaryTaxonomy && <span className="chip">Primary</span>}
              <span className="taxonomy-code">{taxonomy.taxonomyCode}</span>
            </li>
          ))}
        </ul>
      </section>

      <div className="disclaimer-panel">
        <InfoIcon width={18} height={18} />
        <p>
          Provider information is sourced from public NPPES/NPI records and may change. Confirm
          details directly with the provider.
        </p>
      </div>

      <div className="disclaimer-panel">
        <InfoIcon width={18} height={18} />
        <p>Insurance coverage is not verified in this demo. Confirm directly with the provider or insurer.</p>
      </div>

      {detail.importedAt && (
        <p className="provenance-note">
          Imported into DocFit AI on{' '}
          {new Date(detail.importedAt).toLocaleDateString(undefined, {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
          })}{' '}
          from public NPPES/NPI records.
        </p>
      )}
    </article>
  )
}

export default ProviderDetailPage
