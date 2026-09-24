import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { placeOrder } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { getProductIcon } from '../icons/ProductIcons'
import { getTint } from '../icons/tints'

export function CartPage() {
  const { lines, total, updateQuantity, removeItem, clear } = useCart()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [placing, setPlacing] = useState(false)

  async function handleCheckout() {
    if (!user) {
      navigate('/login')
      return
    }
    setError(null)
    setPlacing(true)
    try {
      const order = await placeOrder(total, user.accessToken)
      clear()
      navigate(`/orders/${order.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not place the order')
    } finally {
      setPlacing(false)
    }
  }

  if (lines.length === 0) {
    return (
      <div className="px-16 py-16 text-center text-slate-500">
        Your cart is empty. <a href="/" className="text-indigo-600 underline">Browse the catalog</a>.
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-8 px-16 py-10 lg:flex-row lg:items-start">
      <div className="flex flex-1 flex-col gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Your Cart</h1>
        {lines.map((line) => {
          const tint = getTint(line.product.id)
          const Icon = getProductIcon(line.product.name)
          return (
          <div
            key={line.product.id}
            className="flex items-center justify-between rounded-xl bg-white p-4"
          >
            <div className="flex items-center gap-4">
              <div className={`flex h-16 w-16 shrink-0 items-center justify-center rounded-lg ${tint.bg}`}>
                <Icon className={`h-8 w-8 ${tint.icon}`} />
              </div>
              <div>
                <p className="text-[15px] font-semibold text-slate-900">{line.product.name}</p>
                <p className="text-[13px] text-slate-500">${line.product.price.toFixed(2)} each</p>
              </div>
            </div>
            <div className="flex items-center gap-6">
              <div className="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-1.5">
                <button
                  onClick={() => updateQuantity(line.product.id, line.quantity - 1)}
                  className="text-slate-700"
                  aria-label={`Decrease quantity of ${line.product.name}`}
                >
                  −
                </button>
                <span className="text-sm font-medium text-slate-900">{line.quantity}</span>
                <button
                  onClick={() => updateQuantity(line.product.id, line.quantity + 1)}
                  className="text-slate-700"
                  aria-label={`Increase quantity of ${line.product.name}`}
                >
                  +
                </button>
              </div>
              <button
                onClick={() => removeItem(line.product.id)}
                className="text-[13px] font-medium text-slate-500 hover:text-red-600"
              >
                Remove
              </button>
            </div>
          </div>
          )
        })}
      </div>

      <div className="flex w-full flex-col gap-4 rounded-xl bg-white p-6 lg:w-[360px]">
        <h2 className="text-lg font-bold text-slate-900">Order Summary</h2>
        <div className="flex justify-between text-sm text-slate-500">
          <span>Subtotal</span>
          <span>${total.toFixed(2)}</span>
        </div>
        <div className="flex justify-between text-sm text-slate-500">
          <span>Shipping</span>
          <span>Free</span>
        </div>
        <div className="flex justify-between text-sm font-semibold text-slate-900">
          <span>Total</span>
          <span>${total.toFixed(2)}</span>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          onClick={handleCheckout}
          disabled={placing}
          className="rounded-lg bg-indigo-600 py-3 text-[15px] font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          {placing ? 'Placing order…' : user ? 'Checkout' : 'Log in to checkout'}
        </button>
      </div>
    </div>
  )
}
