import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
}

export function SpecialtyIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M9 3v5a3 3 0 0 0 6 0V3" />
      <path d="M12 11v5a5 5 0 0 0 5 5 5 5 0 0 0 5-5" />
      <circle cx="19" cy="8" r="2" />
    </svg>
  )
}

export function InsuranceIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5 4.5 6.5v5c0 4.5 3 7.7 7.5 9 4.5-1.3 7.5-4.5 7.5-9v-5L12 3.5Z" />
      <path d="m9 12 2.2 2.2L15.5 10" />
    </svg>
  )
}

export function LocationIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 21s7-6.1 7-11.5A7 7 0 0 0 5 9.5C5 14.9 12 21 12 21Z" />
      <circle cx="12" cy="9.5" r="2.4" />
    </svg>
  )
}

export function PhoneIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6.5 3.5h3l1.5 4-2 1.5a11 11 0 0 0 5.5 5.5l1.5-2 4 1.5v3a2 2 0 0 1-2.2 2A16.5 16.5 0 0 1 4.5 5.7 2 2 0 0 1 6.5 3.5Z" />
    </svg>
  )
}

export function BadgeIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="9" r="5.5" />
      <path d="m8.5 13.5-1.7 6.5L12 17.5l5.2 2.5-1.7-6.5" />
    </svg>
  )
}

export function CheckIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="m8.5 12.5 2.3 2.3 4.7-5" />
    </svg>
  )
}

export function InfoIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5.5" />
      <path d="M12 8.2v.1" />
    </svg>
  )
}

export function AlertIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5 21.5 20h-19L12 3.5Z" />
      <path d="M12 10v4" />
      <path d="M12 16.8v.1" />
    </svg>
  )
}

export function SearchIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20 20-4.3-4.3" />
    </svg>
  )
}

export function CrosshairIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v3.5M12 18.5V22M2 12h3.5M18.5 12H22" />
    </svg>
  )
}

export function DirectionsIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M3 11 20 4l-7 17-3-7-7-3Z" />
    </svg>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="m5 5 14 14M19 5 5 19" />
    </svg>
  )
}

export function ChevronLeftIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="m14.5 5-7 7 7 7" />
    </svg>
  )
}

export function ChevronRightIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="m9.5 5 7 7-7 7" />
    </svg>
  )
}

export function CompareIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3.5" y="4" width="7.5" height="16" rx="2" />
      <rect x="13" y="4" width="7.5" height="16" rx="2" />
      <path d="M7.25 9v2M16.75 13v2" />
    </svg>
  )
}

export function ExternalLinkIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M9 6H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3" />
      <path d="M14 4h6v6" />
      <path d="M20 4 11 13" />
    </svg>
  )
}

export function HeartIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 20.2s-7.2-4.5-9.8-9A5.6 5.6 0 0 1 12 5.6a5.6 5.6 0 0 1 9.8 5.6c-2.6 4.5-9.8 9-9.8 9Z" />
    </svg>
  )
}

export function UserIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
    </svg>
  )
}

export function LogOutIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3" />
      <path d="M14 16l4-4-4-4" />
      <path d="M18 12H9" />
    </svg>
  )
}

export function EyeIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="2.6" />
    </svg>
  )
}

export function EyeOffIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M3.5 3.5l17 17" />
      <path d="M10.6 5.7A9.8 9.8 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a15.6 15.6 0 0 1-3.4 4.2M6.7 6.9C4.2 8.6 2.5 12 2.5 12s3.5 6.5 9.5 6.5a9.6 9.6 0 0 0 3.7-.75" />
      <path d="M9.9 10a2.6 2.6 0 0 0 3.9 3.4" />
    </svg>
  )
}

export function BookmarkIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6.5 3.5h11a1 1 0 0 1 1 1V21l-6.5-4-6.5 4V4.5a1 1 0 0 1 1-1Z" />
    </svg>
  )
}

export function ShareIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="18" cy="5" r="2.5" />
      <circle cx="6" cy="12" r="2.5" />
      <circle cx="18" cy="19" r="2.5" />
      <path d="M8.2 10.7 15.8 6.3M8.2 13.3l7.6 4.4" />
    </svg>
  )
}

export function PrimaryCareIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 10.5V20a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-9.5" />
      <path d="M3.5 11 12 4l8.5 7" />
      <path d="M12 13v5M9.5 15.5h5" />
    </svg>
  )
}

export function DermatologyIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5c3.5 3.8 6 7.2 6 10.3a6 6 0 0 1-12 0c0-3.1 2.5-6.5 6-10.3Z" />
      <path d="M9.7 14a2.3 2.3 0 0 0 2.3 2.3" />
    </svg>
  )
}

export function OrthopedicsIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6.5 6.5a2.3 2.3 0 1 1 3.6 2.8l5 5a2.3 2.3 0 1 1-2.3 2.3l-5-5A2.3 2.3 0 1 1 6.5 6.5Z" />
      <path d="m9 9 6 6" />
    </svg>
  )
}

export function PsychiatryIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M9.5 4.5a4 4 0 0 1 4 4v.3a3.5 3.5 0 0 1 1.8 6.4 3.2 3.2 0 0 1-3 4.3h-1a2.5 2.5 0 0 1-2.5-2.5v-1.5a3 3 0 0 1-3-3 3 3 0 0 1 .8-2 3.5 3.5 0 0 1 3-6Z" />
      <path d="M9.5 12.5a2 2 0 0 0 2 2" />
    </svg>
  )
}

export function LockIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V7.5a4 4 0 0 1 8 0V11" />
      <path d="M12 14.5v2" />
    </svg>
  )
}

export function MapPinIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 21s7-6.1 7-11.5A7 7 0 0 0 5 9.5C5 14.9 12 21 12 21Z" />
      <path d="M9.3 9.5h5.4M12 7.2v4.6" />
    </svg>
  )
}
