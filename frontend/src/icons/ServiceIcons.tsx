import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

export function CartIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 8h12l-1 12.5a1 1 0 0 1-1 .5H8a1 1 0 0 1-1-.5L6 8Z" />
      <path d="M9 8V6a3 3 0 0 1 6 0v2" />
    </svg>
  )
}

export function CardIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="2.5" y="5.5" width="19" height="13" rx="2" />
      <path d="M2.5 10h19M6 14.5h4" />
    </svg>
  )
}

export function ReceiptIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 2h12v20l-2-1.4L14 22l-2-1.4L10 22l-2-1.4L6 22V2Z" />
      <path d="M8.5 7h7M8.5 11h7M8.5 15h4.5" />
    </svg>
  )
}

export function BellIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 8a6 6 0 0 1 12 0c0 4 1.5 5.3 2 6H4c.5-.7 2-2 2-6Z" />
      <path d="M10 19.5a2 2 0 0 0 4 0" />
    </svg>
  )
}

export function CheckBadgeIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M8 12.3l2.6 2.6L16 9" />
    </svg>
  )
}

export function XBadgeIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M9 9l6 6M15 9l-6 6" />
    </svg>
  )
}

export function RefundIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M9 14.5l-4.5-4.5L9 5.5" />
      <path d="M4.5 10h9.5a5.5 5.5 0 0 1 5.5 5.5v.5" />
    </svg>
  )
}

export function KeyIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="8" cy="15" r="4.5" />
      <path d="M11.2 11.8 20 3M15.5 6.5 18 9M18.5 3.5 21 6" />
    </svg>
  )
}

export function CubeIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3 4 7.5v9L12 21l8-4.5v-9L12 3Z" />
      <path d="M4 7.5 12 12l8-4.5M12 12v9" />
    </svg>
  )
}

export function RouterIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="10" width="18" height="8" rx="2" />
      <path d="M7 10V7a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v3M7 14h.01M11 14h4" />
    </svg>
  )
}

export function CompassIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="m14.5 9.5-1.8 4.2-4.2 1.8 1.8-4.2z" />
    </svg>
  )
}
