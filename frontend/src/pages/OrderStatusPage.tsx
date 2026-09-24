import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fetchOrder } from '../api/client'
import { ServiceFlowDiagram } from '../components/ServiceFlowDiagram'
import { useAuth } from '../context/AuthContext'
import type { Order, OrderStatus } from '../types'

const TERMINAL_STATUSES: OrderStatus[] = ['COMPLETED', 'PAYMENT_FAILED', 'CANCELLED']
const POLL_INTERVAL_MS = 3000

export function OrderStatusPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const { user } = useAuth()
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!orderId || !user) return

    let cancelled = false
    let intervalId: ReturnType<typeof setInterval> | undefined

    async function poll() {
      try {
        const latest = await fetchOrder(orderId!, user!.accessToken)
        if (cancelled) return
        setOrder(latest)
        if (TERMINAL_STATUSES.includes(latest.status) && intervalId) {
          clearInterval(intervalId)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Could not load the order')
      }
    }

    poll()
    intervalId = setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      if (intervalId) clearInterval(intervalId)
    }
  }, [orderId, user])

  if (!user) {
    return <p className="px-16 py-16 text-slate-500">Log in to view this order.</p>
  }
  if (error) {
    return <p className="px-16 py-16 text-red-600">Couldn't load order {orderId}: {error}</p>
  }
  if (!order) {
    return <p className="px-16 py-16 text-slate-500">Loading order…</p>
  }

  const failed = order.status === 'PAYMENT_FAILED' || order.status === 'CANCELLED'
  const shortId = order.id.slice(0, 8)

  return (
    <div className="flex justify-center px-16 py-16">
      <div className="flex w-full max-w-[760px] flex-col gap-8 rounded-2xl bg-white p-10">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-slate-900" title={order.id}>
              Order #{shortId}
            </h2>
            <p className="mt-1 text-[13px] text-slate-500">${order.amount.toFixed(2)}</p>
          </div>
          <span
            className={`rounded-full px-4 py-2 text-[13px] font-semibold ${
              failed ? 'bg-red-100 text-red-700' : 'bg-indigo-50 text-indigo-600'
            }`}
          >
            {order.status}
          </span>
        </div>

        <ServiceFlowDiagram status={order.status} />

        <p className="text-[13px] text-slate-500">
          {order.status === 'COMPLETED' && 'Your order is complete. Thanks for shopping with us!'}
          {order.status === 'PAYMENT_FAILED' && 'The payment was declined - nothing was charged.'}
          {order.status === 'CANCELLED' && 'This order was cancelled and refunded.'}
          {!TERMINAL_STATUSES.includes(order.status) &&
            'This page updates automatically as the order moves through the saga, live across order-service, payment-service, invoice-service, and notification-service.'}
        </p>
      </div>
    </div>
  )
}
