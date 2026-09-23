import { getProductIcon } from '../icons/ProductIcons'
import { getTint } from '../icons/tints'
import { useCart } from '../context/CartContext'
import type { Product } from '../types'

export function ProductCard({ product }: { product: Product }) {
  const { addItem } = useCart()
  const tint = getTint(product.id)
  const Icon = getProductIcon(product.name)

  return (
    <div className="flex flex-1 flex-col gap-3 rounded-xl bg-white p-4 shadow-sm">
      <div className={`flex h-40 w-full items-center justify-center rounded-lg ${tint.bg}`}>
        <Icon className={`h-16 w-16 ${tint.icon}`} />
      </div>
      <h3 className="text-[15px] font-semibold text-slate-900">{product.name}</h3>
      <p className="text-lg font-bold text-indigo-600">${product.price.toFixed(2)}</p>
      <button
        onClick={() => addItem(product)}
        className="rounded-lg bg-indigo-600 py-2.5 text-sm font-medium text-white hover:bg-indigo-700"
      >
        Add to Cart
      </button>
    </div>
  )
}
