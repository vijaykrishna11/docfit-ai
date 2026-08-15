import HeroIllustration from './HeroIllustration'
import { CheckIcon } from './icons'

const TRUST_INDICATORS = ['Real NPI provider data', 'Location-based search', 'No account required']

function Hero() {
  return (
    <section className="hero">
      <div className="container hero-inner">
        <div className="hero-copy-col">
          <p className="eyebrow">Healthcare navigation, simplified</p>
          <h1>Find the right healthcare provider, without the directory maze.</h1>
          <p className="hero-copy">
            Search real provider data by specialty and location, with insurance-aware tools
            designed to make healthcare navigation simpler.
          </p>
          <ul className="trust-list">
            {TRUST_INDICATORS.map((item) => (
              <li key={item}>
                <CheckIcon width={16} height={16} />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <HeroIllustration />
      </div>
    </section>
  )
}

export default Hero
