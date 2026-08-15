import type { SVGProps } from 'react'
import type { SpecialtyDto } from '../api/types'
import { DermatologyIcon, HeartIcon, OrthopedicsIcon, PrimaryCareIcon, PsychiatryIcon } from './icons'

type IconComponent = (props: SVGProps<SVGSVGElement>) => React.JSX.Element

const SPECIALTY_ICONS: Record<string, IconComponent> = {
  PRIMARY_CARE: PrimaryCareIcon,
  CARDIOLOGY: HeartIcon,
  DERMATOLOGY: DermatologyIcon,
  ORTHOPEDICS: OrthopedicsIcon,
  PSYCHIATRY_MENTAL_HEALTH: PsychiatryIcon,
}

interface PopularSpecialtiesProps {
  specialties: SpecialtyDto[]
  onSelect: (code: string) => void
}

function PopularSpecialties({ specialties, onSelect }: PopularSpecialtiesProps) {
  if (specialties.length === 0) {
    return null
  }

  return (
    <section className="page-section" aria-labelledby="popular-specialties-heading">
      <div className="container">
        <h2 id="popular-specialties-heading">Find care by specialty</h2>
        <p className="section-intro">
          Jump straight to a search for the type of care you need. You can always change this later.
        </p>
        <ul className="specialty-shortcut-grid">
          {specialties.map((specialty) => {
            const Icon = SPECIALTY_ICONS[specialty.code] ?? PrimaryCareIcon
            return (
              <li key={specialty.code}>
                <button type="button" className="specialty-shortcut-card" onClick={() => onSelect(specialty.code)}>
                  <span className="specialty-shortcut-icon" aria-hidden="true">
                    <Icon width={22} height={22} />
                  </span>
                  {specialty.name}
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </section>
  )
}

export default PopularSpecialties
