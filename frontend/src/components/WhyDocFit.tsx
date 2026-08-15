import { BadgeIcon, EyeIcon, LockIcon } from './icons'

const REASONS = [
  {
    icon: EyeIcon,
    title: 'Transparent results',
    body: 'Every provider detail page shows why it appeared in your search -- matched specialty, approximate distance, and where the record came from.',
  },
  {
    icon: LockIcon,
    title: 'Privacy-first search',
    body: "Search for care without creating an account. We don't track or store your searches unless you explicitly choose to save one.",
  },
  {
    icon: BadgeIcon,
    title: 'Real public provider data',
    body: 'Provider identity and practice information come from the public NPPES/NPI registry -- not scraped listings or fabricated profiles.',
  },
]

function WhyDocFit() {
  return (
    <section className="page-section" aria-labelledby="why-docfit-heading">
      <div className="container">
        <h2 id="why-docfit-heading">Healthcare directories shouldn&rsquo;t feel like a maze.</h2>
        <p className="section-intro">DocFit AI is built around three simple ideas.</p>
        <ul className="fact-grid why-docfit-grid">
          {REASONS.map((reason) => (
            <li key={reason.title} className="fact-card">
              <span className="fact-card-icon" aria-hidden="true">
                <reason.icon width={18} height={18} />
              </span>
              <h3>{reason.title}</h3>
              <p>{reason.body}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

export default WhyDocFit
