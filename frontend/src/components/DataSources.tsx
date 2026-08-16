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
  return (
    <section className="page-section page-section-muted" id="data-sources">
      <div className="container">
        <h2>Data sources &amp; transparency</h2>
        <p className="section-intro">DocFit AI is built on real public data, with clear limits on what it can promise.</p>
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
