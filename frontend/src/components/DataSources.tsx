import { useEffect, useState } from 'react'
import { fetchCoverage } from '../api/client'
import type { CoverageDto } from '../api/types'
import { BadgeIcon, CheckIcon, InfoIcon, LocationIcon } from './icons'

const FACTS = [
  {
    icon: BadgeIcon,
    title: 'Real public provider records',
    text: 'Provider identities and practice information come from the public NPPES/NPI Registry.',
  },
  {
    icon: LocationIcon,
    title: 'Approximate distance',
    text: 'Distance is a straight-line calculation, not driving distance or time.',
  },
  {
    icon: CheckIcon,
    title: 'Records can change',
    text: 'Provider information can change. Always confirm details directly with the provider.',
  },
  {
    icon: InfoIcon,
    title: 'Network evidence, not a guarantee',
    text: 'When a plan with directory evidence is selected, DocFit AI shows sourced, dated network directory evidence -- never a guarantee of coverage or payment. Most insurers have no integrated directory yet.',
  },
]

function DataSources() {
  const [coverage, setCoverage] = useState<CoverageDto | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchCoverage()
      .then((result) => {
        if (!cancelled) setCoverage(result)
      })
      .catch(() => {
        // Non-fatal: the static facts below still render without the live coverage panel.
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="page-section page-section-muted" id="data-sources">
      <div className="container">
        <h2>Data sources &amp; transparency</h2>
        <p className="section-intro">DocFit AI is built on real public data, with clear limits on what it can promise.</p>

        {coverage && (
          <div className="coverage-panel" role="status">
            <h3>Current DocFit AI coverage</h3>
            <dl className="coverage-grid">
              <div>
                <dt>Providers</dt>
                <dd>{coverage.providerCount.toLocaleString()}</dd>
              </div>
              <div>
                <dt>Practice locations</dt>
                <dd>{coverage.locationCount.toLocaleString()}</dd>
              </div>
              <div>
                <dt>Specialty categories</dt>
                <dd>{coverage.specialtyCount}</dd>
              </div>
              <div>
                <dt>Cities with imported provider data</dt>
                <dd>{coverage.providerCityCount.toLocaleString()}</dd>
              </div>
            </dl>
            <p className="field-hint">
              Currently imported provider data covers:{' '}
              {coverage.sampleProviderAreas.join(', ') || 'no areas yet'}
              {coverage.sampleProviderAreasTruncated ? ', and more' : ''}.
            </p>
            {coverage.geographyZipCount > 0 && (
              <p className="field-hint">
                DocFit AI also has reference geography (ZIP/city/county lookup data, not provider
                records) for {coverage.geographyZipCount.toLocaleString()} ZIP codes across{' '}
                {coverage.geographyCityCount.toLocaleString()} cities
                {coverage.geographySource ? ` (source: ${coverage.geographySource})` : ''}. This is
                not the same as having provider data for all of those areas.
              </p>
            )}
            <p className="field-hint">
              These are real, live counts from DocFit AI's own database -- not marketing figures.
              Coverage today is partial; it does not represent full county or statewide coverage.
            </p>
          </div>
        )}

        <ul className="fact-grid">
          {FACTS.map((fact) => (
            <li className="fact-card" key={fact.title}>
              <div className="fact-card-icon">
                <fact.icon width={18} height={18} />
              </div>
              <h3>{fact.title}</h3>
              <p>{fact.text}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

export default DataSources
