const TINTS = [
  { bg: 'bg-amber-100', icon: 'text-amber-700' },
  { bg: 'bg-blue-100', icon: 'text-blue-700' },
  { bg: 'bg-pink-100', icon: 'text-pink-700' },
  { bg: 'bg-green-100', icon: 'text-green-700' },
  { bg: 'bg-orange-100', icon: 'text-orange-700' },
  { bg: 'bg-indigo-100', icon: 'text-indigo-700' },
  { bg: 'bg-red-100', icon: 'text-red-700' },
  { bg: 'bg-emerald-100', icon: 'text-emerald-700' },
]

/** Deterministic per-product color pairing, keyed by product id so the same product always
 * gets the same tint wherever it's shown (catalog grid, cart line items, ...). */
export function getTint(productId: number): { bg: string; icon: string } {
  return TINTS[productId % TINTS.length]
}
