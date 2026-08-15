import { CompareIcon, LocationIcon, SpecialtyIcon } from './icons'

const STEPS = [
  {
    icon: SpecialtyIcon,
    title: 'Choose your specialty',
    copy: 'Pick the type of care you need, from primary care to psychiatry.',
  },
  {
    icon: LocationIcon,
    title: 'Tell us where you are',
    copy: 'Enter a ZIP code or city, or share your location, then pick a search radius.',
  },
  {
    icon: CompareIcon,
    title: 'Compare nearby providers',
    copy: 'Review real provider details side by side and reach out directly.',
  },
]

function HowItWorks() {
  return (
    <section className="page-section" id="how-it-works">
      <div className="container">
        <h2>How it works</h2>
        <ol className="step-grid">
          {STEPS.map((step, index) => (
            <li className="step-card" key={step.title}>
              <span className="step-number" aria-hidden="true">
                {String(index + 1).padStart(2, '0')}
              </span>
              <div className="step-icon">
                <step.icon width={22} height={22} />
              </div>
              <h3>{step.title}</h3>
              <p>{step.copy}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

export default HowItWorks
