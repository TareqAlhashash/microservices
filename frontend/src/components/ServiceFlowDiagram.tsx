import type { ComponentType, SVGProps } from 'react'
import { BellIcon, CardIcon, CartIcon, CheckBadgeIcon, ReceiptIcon, RefundIcon, XBadgeIcon } from '../icons/ServiceIcons'
import type { OrderStatus } from '../types'

type NodeState = 'pending' | 'active' | 'done' | 'failed' | 'skipped'

interface FlowNode {
  key: string
  service: string
  label: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
  state: NodeState
}

const STYLES: Record<NodeState, { ring: string; bg: string; icon: string; text: string }> = {
  pending: { ring: '', bg: 'bg-slate-100', icon: 'text-slate-400', text: 'text-slate-400' },
  active: { ring: 'ring-4 ring-indigo-100', bg: 'bg-indigo-600', icon: 'text-white', text: 'text-slate-900' },
  done: { ring: '', bg: 'bg-green-600', icon: 'text-white', text: 'text-slate-900' },
  failed: { ring: 'ring-4 ring-red-100', bg: 'bg-red-600', icon: 'text-white', text: 'text-red-700' },
  skipped: { ring: '', bg: 'bg-white', icon: 'text-slate-300', text: 'text-slate-300' },
}

function buildNodes(status: OrderStatus): FlowNode[] {
  const order: FlowNode = { key: 'order', service: 'order-service', label: 'Order placed', icon: CartIcon, state: 'done' }
  const payment: FlowNode = { key: 'payment', service: 'payment-service', label: 'Payment captured', icon: CardIcon, state: 'pending' }
  const invoice: FlowNode = { key: 'invoice', service: 'invoice-service', label: 'Invoice issued', icon: ReceiptIcon, state: 'pending' }
  const notification: FlowNode = { key: 'notification', service: 'notification-service', label: 'Customer notified', icon: BellIcon, state: 'pending' }
  const complete: FlowNode = { key: 'complete', service: 'order-service', label: 'Order completed', icon: CheckBadgeIcon, state: 'pending' }

  switch (status) {
    case 'PLACED':
      payment.state = 'active'
      return [order, payment, invoice, notification, complete]
    case 'PAID':
      payment.state = 'done'
      invoice.state = 'active'
      return [order, payment, invoice, notification, complete]
    case 'INVOICED':
      payment.state = 'done'
      invoice.state = 'done'
      notification.state = 'active'
      return [order, payment, invoice, notification, complete]
    case 'COMPLETED':
      payment.state = 'done'
      invoice.state = 'done'
      notification.state = 'done'
      complete.state = 'done'
      return [order, payment, invoice, notification, complete]
    case 'PAYMENT_FAILED':
      payment.state = 'failed'
      payment.label = 'Payment declined'
      invoice.state = 'skipped'
      notification.state = 'skipped'
      return [
        order,
        payment,
        { key: 'stopped', service: '—', label: 'Nothing charged', icon: XBadgeIcon, state: 'skipped' },
      ]
    case 'CANCELLED':
      payment.state = 'done'
      return [
        order,
        payment,
        {
          key: 'refunded',
          service: 'payment-service',
          label: 'Refunded, order cancelled',
          icon: RefundIcon,
          state: 'failed',
        },
      ]
    default:
      return [order, payment, invoice, notification, complete]
  }
}

function ConnectorLine({ filled }: { filled: boolean }) {
  return <div className={`h-[3px] flex-1 self-start mt-6 ${filled ? 'bg-green-600' : 'bg-slate-200'}`} />
}

export function ServiceFlowDiagram({ status }: { status: OrderStatus }) {
  const nodes = buildNodes(status)

  return (
    <div className="flex items-start gap-1">
      {nodes.map((node, i) => {
        const style = STYLES[node.state]
        return (
          <div className="flex flex-1 items-start" key={node.key}>
            <div className="flex flex-1 flex-col items-center gap-2 text-center">
              <div className="relative flex items-center justify-center">
                {node.state === 'active' && (
                  <span className="absolute inline-flex h-12 w-12 animate-ping rounded-full bg-indigo-400 opacity-50" />
                )}
                <div
                  className={`relative flex h-12 w-12 items-center justify-center rounded-full ${style.bg} ${style.ring}`}
                >
                  <node.icon className={`h-6 w-6 ${style.icon}`} />
                </div>
              </div>
              <div>
                <p className={`text-[13px] font-semibold ${style.text}`}>{node.label}</p>
                <p className="text-[11px] text-slate-400">{node.service}</p>
              </div>
            </div>
            {i < nodes.length - 1 && <ConnectorLine filled={node.state === 'done'} />}
          </div>
        )
      })}
    </div>
  )
}
