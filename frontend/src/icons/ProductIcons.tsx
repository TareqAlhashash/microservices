import type { ComponentType, SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

function HeadphonesIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 14v-2a8 8 0 0 1 16 0v2" />
      <rect x="2" y="14" width="4.5" height="7" rx="2" />
      <rect x="17.5" y="14" width="4.5" height="7" rx="2" />
    </svg>
  )
}

function KeyboardIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="2" y="6" width="20" height="12" rx="2" />
      <path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M6 14h12" />
    </svg>
  )
}

function EspressoIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 8h13a3 3 0 0 1 0 6h-1" />
      <path d="M4 8v7a3 3 0 0 0 3 3h5a3 3 0 0 0 3-3v-2" />
      <path d="M8 3c0 1-1 1-1 2M12 3c0 1-1 1-1 2" />
    </svg>
  )
}

function ShoeIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M3 18v-4c0-1 .8-1.9 1.8-2.1l2.6-.6c.6-.1 1.1-.5 1.4-1.1l.8-1.5c.4-.7 1.3-1 2-.6l3.6 1.9c.5.3.8.8.8 1.4v1c0 .3.2.6.5.7l3.5 1.2c.9.3 1.5 1.1 1.5 2v1.7H3Z" />
      <path d="M3 18h18M10 11l1.5 2M13 10l1.5 2" />
    </svg>
  )
}

function BottleIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="9" y="2" width="6" height="3" rx="1" />
      <path d="M8.5 6h7l1 3v11.5a1.5 1.5 0 0 1-1.5 1.5h-6a1.5 1.5 0 0 1-1.5-1.5V9l1-3Z" />
      <path d="M7.5 12h9" />
    </svg>
  )
}

function CameraIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M8 7l1.3-2.5h5.4L16 7" />
      <circle cx="12" cy="13.5" r="3.5" />
    </svg>
  )
}

function CookwareIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 11h16v2.5a5 5 0 0 1-5 5h-6a5 5 0 0 1-5-5V11Z" />
      <path d="M2 11h20" />
      <path d="M12 11V7.5" />
      <circle cx="12" cy="5.5" r="1.5" />
    </svg>
  )
}

function DeskIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="4" y="4" width="15" height="9" rx="1" />
      <path d="M7.5 19.5h8M11.5 13v6.5" />
      <path d="M4 19.5l1-6.5M19 19.5l-1-6.5" />
    </svg>
  )
}

function BackpackIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M7 8.5a5 5 0 0 1 10 0V11h1a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2h1V8.5Z" />
      <path d="M9 8.5V12h6V8.5M9.5 15.5h5" />
    </svg>
  )
}

function LightStripIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="10" width="18" height="4" rx="2" />
      <path d="M6.5 10v4M10.5 10v4M14.5 10v4M18.5 10v4" />
    </svg>
  )
}

function SkilletIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="9.5" cy="12" r="6.5" />
      <path d="M15.5 10.5H20a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1h-4.5" />
      <path d="M7 12a2.5 2.5 0 0 1 2.5-2.5" />
    </svg>
  )
}

function SpeakerIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="7" y="2" width="10" height="20" rx="3" />
      <circle cx="12" cy="8.5" r="2.3" />
      <circle cx="12" cy="16" r="2.3" />
    </svg>
  )
}

function BlanketIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M3 10.3h18M3 14.7h18M9 5v14M15 5v14" />
    </svg>
  )
}

function KettleIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 9a6 6 0 0 1 12 0v6a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V9Z" />
      <path d="M18 9h1.5a2 2 0 0 1 0 4H18M9.5 5V3h5v2" />
    </svg>
  )
}

function PackageIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M21 8.5l-9-5-9 5 9 5 9-5Z" />
      <path d="M3 8.5v7l9 5 9-5v-7" />
      <path d="M12 13.5V20.5" />
    </svg>
  )
}

const PRODUCT_ICONS: Record<string, ComponentType<IconProps>> = {
  'Wireless Noise-Cancelling Headphones': HeadphonesIcon,
  'Mechanical Keyboard': KeyboardIcon,
  'Espresso Machine': EspressoIcon,
  'Trail Running Shoes': ShoeIcon,
  'Stainless Steel Water Bottle': BottleIcon,
  '4K Action Camera': CameraIcon,
  'Ceramic Cookware Set': CookwareIcon,
  'Standing Desk Converter': DeskIcon,
  'Leather Backpack': BackpackIcon,
  'Smart LED Light Strip': LightStripIcon,
  'Cast Iron Skillet': SkilletIcon,
  'Portable Bluetooth Speaker': SpeakerIcon,
  'Weighted Blanket': BlanketIcon,
  'Electric Kettle': KettleIcon,
}

/** Falls back to a generic package icon for any product name the seed list above doesn't know. */
export function getProductIcon(productName: string): ComponentType<IconProps> {
  return PRODUCT_ICONS[productName] ?? PackageIcon
}
