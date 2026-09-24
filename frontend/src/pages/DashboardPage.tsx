import { Fragment, useEffect, useState } from 'react'
import { fetchOrderEvents, fetchOrders, fetchServiceStatuses } from '../api/client'
import { useAuth } from '../context/AuthContext'
import {
  BellIcon,
  CardIcon,
  CartIcon,
  CompassIcon,
  CubeIcon,
  KeyIcon,
  ReceiptIcon,
  RouterIcon,
} from '../icons/ServiceIcons'
import type { OrderEventLog, OrderStatus, PagedOrders, ServiceStatus } from '../types'

const SERVICE_ICONS: Record<string, typeof CartIcon> = {
  'eureka-server': CompassIcon,
  'api-gateway': RouterIcon,
  'auth-service': KeyIcon,
  'order-service': CartIcon,
  'payment-service': CardIcon,
  'invoice-service': ReceiptIcon,
  'notification-service': BellIcon,
}

const SERVICES_POLL_MS = 5000
const ORDERS_POLL_MS = 4000
const ORDERS_PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

const STATUS_BADGE: Record<OrderStatus, string> = {
  PLACED: 'bg-indigo-50 text-indigo-600',
  PAID: 'bg-indigo-50 text-indigo-600',
  INVOICED: 'bg-indigo-50 text-indigo-600',
  COMPLETED: 'bg-green-100 text-green-700',
  PAYMENT_FAILED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-red-100 text-red-700',
}

function ServiceStatusGrid({ services }: { services: ServiceStatus[] }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
      {services.map((service) => {
        const Icon = SERVICE_ICONS[service.name] ?? CubeIcon
        const up = service.status === 'UP'
        return (
          <div key={service.name} className="flex flex-col items-center gap-2 rounded-xl bg-white p-4 text-center">
            <div className="relative flex items-center justify-center">
              {up && <span className="absolute inline-flex h-11 w-11 animate-pulse rounded-full bg-green-100" />}
              <div
                className={`relative flex h-11 w-11 items-center justify-center rounded-full ${
                  up ? 'bg-green-600' : 'bg-slate-300'
                }`}
              >
                <Icon className="h-5 w-5 text-white" />
              </div>
            </div>
            <p className="text-[13px] font-semibold text-slate-900">{service.name}</p>
            <span
              className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                up ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
              }`}
            >
              {service.status}
            </span>
          </div>
        )
      })}
    </div>
  )
}

function OrderEventsList({ events, loading }: { events: OrderEventLog[] | undefined; loading: boolean }) {
  if (loading) {
    return <p className="px-4 py-3 text-[13px] text-slate-500">Loading logs…</p>
  }
  if (!events || events.length === 0) {
    return <p className="px-4 py-3 text-[13px] text-slate-500">No events recorded yet.</p>
  }
  return (
    <ul className="flex flex-col gap-1.5 px-4 py-3">
      {events.map((event, i) => (
        <li key={i} className="flex items-start gap-3 font-mono text-[12px]">
          <span className="shrink-0 text-slate-400">{new Date(event.occurredAt).toLocaleTimeString()}</span>
          <span
            className={`shrink-0 rounded px-1.5 py-0.5 text-[11px] font-semibold ${
              event.eventType.includes('Failed') || event.eventType.includes('Refunded')
                ? 'bg-red-50 text-red-700'
                : 'bg-slate-100 text-slate-600'
            }`}
          >
            {event.eventType}
          </span>
          <span className="text-slate-700">{event.message}</span>
        </li>
      ))}
    </ul>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const [services, setServices] = useState<ServiceStatus[]>([])
  const [ordersPage, setOrdersPage] = useState<PagedOrders | null>(null)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)
  const [expandedOrderId, setExpandedOrderId] = useState<string | null>(null)
  const [eventsByOrder, setEventsByOrder] = useState<Record<string, OrderEventLog[]>>({})
  const [loadingEventsFor, setLoadingEventsFor] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function poll() {
      try {
        const statuses = await fetchServiceStatuses()
        if (!cancelled) setServices(statuses)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Could not load service status')
      }
    }
    poll()
    const intervalId = setInterval(poll, SERVICES_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(intervalId)
    }
  }, [])

  // Debounced so typing a search term doesn't fire a request per keystroke - the search itself
  // runs server-side (see api/client.ts), not a client-side filter over a fetched page.
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setDebouncedSearch(search)
      setPage(0)
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timeoutId)
  }, [search])

  useEffect(() => {
    if (!user) return
    let cancelled = false
    async function poll() {
      try {
        const latest = await fetchOrders(user!.accessToken, page, ORDERS_PAGE_SIZE, debouncedSearch)
        if (cancelled) return
        setOrdersPage(latest)
        // A page that was valid before (e.g. a search just narrowed the result set, or the
        // orders driving pagination changed underneath a stale page) may no longer exist -
        // snap back to the last real page instead of showing a permanently empty table.
        if (latest.totalPages > 0 && page >= latest.totalPages) {
          setPage(latest.totalPages - 1)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Could not load orders')
      }
    }
    poll()
    const intervalId = setInterval(poll, ORDERS_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(intervalId)
    }
  }, [user, page, debouncedSearch])

  async function toggleExpand(orderId: string) {
    if (expandedOrderId === orderId) {
      setExpandedOrderId(null)
      return
    }
    setExpandedOrderId(orderId)
    if (!eventsByOrder[orderId] && user) {
      setLoadingEventsFor(orderId)
      try {
        const events = await fetchOrderEvents(orderId, user.accessToken)
        setEventsByOrder((current) => ({ ...current, [orderId]: events }))
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not load order events')
      } finally {
        setLoadingEventsFor(null)
      }
    }
  }

  if (!user) {
    return <p className="px-16 py-16 text-slate-500">Log in to view the dashboard.</p>
  }

  const orders = ordersPage?.content ?? []
  const totalElements = ordersPage?.totalElements ?? 0
  const totalPages = Math.max(1, ordersPage?.totalPages ?? 1)

  return (
    <div className="flex flex-col gap-8 px-16 py-10">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">System Dashboard</h1>
        <p className="mt-1 text-[13px] text-slate-500">
          Live service health and every order's saga timeline, updating automatically.
        </p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-slate-700">Services</h2>
        <ServiceStatusGrid services={services} />
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700">Orders</h2>
          <input
            type="text"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by order id…"
            className="w-64 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-indigo-500"
          />
        </div>

        <div className="overflow-hidden rounded-xl bg-white">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-[12px] uppercase tracking-wide text-slate-400">
                <th className="px-4 py-3 font-medium">Order</th>
                <th className="px-4 py-3 font-medium">Customer</th>
                <th className="px-4 py-3 font-medium">Amount</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Placed</th>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                    {ordersPage === null
                      ? 'Loading orders…'
                      : debouncedSearch
                        ? 'No orders match that search.'
                        : 'No orders yet.'}
                  </td>
                </tr>
              )}
              {orders.map((order) => {
                const expanded = expandedOrderId === order.id
                return (
                  <Fragment key={order.id}>
                    <tr
                      onClick={() => toggleExpand(order.id)}
                      className="cursor-pointer border-b border-slate-50 hover:bg-slate-50"
                    >
                      <td className="px-4 py-3 font-mono text-[13px] text-slate-700">
                        <span className={`mr-2 inline-block transition-transform ${expanded ? 'rotate-90' : ''}`}>
                          ›
                        </span>
                        {order.id.slice(0, 8)}
                      </td>
                      <td className="px-4 py-3 text-slate-700">{order.customerEmail}</td>
                      <td className="px-4 py-3 font-semibold text-slate-900">${order.amount.toFixed(2)}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`rounded-full px-2.5 py-1 text-[12px] font-semibold ${STATUS_BADGE[order.status]}`}
                        >
                          {order.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-500">{new Date(order.createdAt).toLocaleString()}</td>
                    </tr>
                    {expanded && (
                      <tr>
                        <td colSpan={5} className="border-b border-slate-50 bg-slate-50 p-0">
                          <OrderEventsList
                            events={eventsByOrder[order.id]}
                            loading={loadingEventsFor === order.id}
                          />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>

        {totalElements > 0 && (
          <div className="flex items-center justify-between text-[13px] text-slate-500">
            <span>
              Showing {page * ORDERS_PAGE_SIZE + 1}–{Math.min((page + 1) * ORDERS_PAGE_SIZE, totalElements)} of{' '}
              {totalElements}
            </span>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
