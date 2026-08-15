import { BadgeIcon, CheckIcon, InfoIcon, LocationIcon } from './icons'

const FACTS = [
  {
    icon: BadgeIcon,
    text: 'Provider identities and practice information come from the public NPPES/NPI Registry.',
  },
  {
    icon: LocationIcon,
    text: 'Distance is an approximate straight-line calculation, not driving distance.',
  },
  {
    icon: CheckIcon,
    text: 'Provider records can change. Always confirm details directly with the provider.',
  },
  {
    icon: InfoIcon,
    text: 'Insurance selection is demo/informational only and is not verified against real coverage.',
  },
]

function DataSources() {
  return (
    <section className="page-section page-section-muted" id="data-sources">
      <div className="container">
        <h2>Data sources &amp; transparency</h2>
        <p className="section-intro">DocFit AI is built on real public data, with clear limits on what it can promise.</p>
        <ul className="fact-list">
          {FACTS.map((fact) => (
            <li key={fact.text}>
              <fact.icon width={18} height={18} />
              <span>{fact.text}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

export default DataSources
