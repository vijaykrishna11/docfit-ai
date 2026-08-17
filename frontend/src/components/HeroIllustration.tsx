import { LocationIcon } from './icons'

/**
 * Decorative-only illustration: an abstract map surface with location pins and a floating
 * example result card. Not tied to real search data -- purely to visually anchor the
 * "location-based provider discovery" concept on the hero.
 */
function HeroIllustration() {
  return (
    <div className="hero-illustration" aria-hidden="true">
      <svg viewBox="0 0 420 360" className="hero-illustration-map" xmlns="http://www.w3.org/2000/svg">
        <rect x="0.75" y="0.75" width="418.5" height="358.5" rx="28" fill="var(--primary-bg)" stroke="var(--primary-border)" strokeWidth="1.5" />

        <path
          d="M16 258c96-46 158 34 268-18 54-26 78-70 120-92"
          stroke="var(--border-strong)"
          strokeWidth="3"
          strokeLinecap="round"
          fill="none"
          opacity="0.55"
        />
        <path
          d="M52 44c70 46 56 118 150 110 62-5 96-58 154-16"
          stroke="var(--border-strong)"
          strokeWidth="3"
          strokeLinecap="round"
          fill="none"
          opacity="0.55"
        />

        <circle cx="208" cy="176" r="118" stroke="var(--primary-border)" strokeWidth="1.5" fill="none" opacity="0.45" />
        <circle cx="208" cy="176" r="78" stroke="var(--primary-border)" strokeWidth="1.5" fill="none" opacity="0.6" />
        <circle cx="208" cy="176" r="40" stroke="var(--primary)" strokeWidth="1.5" fill="none" opacity="0.7" />

        <g transform="translate(122 224)">
          <path d="M0 0c-12.5 0-21.5 9-21.5 21.5C-21.5 44 0 46 0 46s21.5-2 21.5-24.5C21.5 9 12.5 0 0 0Z" fill="var(--secondary)" opacity="0.9" />
          <circle cx="0" cy="19" r="7" fill="var(--surface)" />
        </g>
        <g transform="translate(292 100)">
          <path d="M0 0c-12.5 0-21.5 9-21.5 21.5C-21.5 44 0 46 0 46s21.5-2 21.5-24.5C21.5 9 12.5 0 0 0Z" fill="var(--primary-hover)" opacity="0.9" />
          <circle cx="0" cy="19" r="7" fill="var(--surface)" />
        </g>
        <g transform="translate(208 146)">
          <path d="M0 0c-15 0-26 11-26 26C-26 53 0 56 0 56s26-3 26-30C26 11 15 0 0 0Z" fill="var(--primary)" />
          <circle cx="0" cy="23" r="8.5" fill="var(--surface)" />
        </g>
      </svg>

      <div className="hero-floating-card">
        <span className="hero-floating-avatar">SC</span>
        <div>
          <p className="hero-floating-title">Cardiology</p>
          <p className="hero-floating-meta">
            <LocationIcon width={12} height={12} />
            1.4 mi away
          </p>
        </div>
      </div>
    </div>
  )
}

export default HeroIllustration
