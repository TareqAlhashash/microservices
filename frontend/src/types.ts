export interface Product {
  id: number
  name: string
  description: string
  price: number
  imageUrl: string
}

export interface CartLine {
  product: Product
  quantity: number
}

export type OrderStatus = 'PLACED' | 'PAID' | 'INVOICED' | 'COMPLETED' | 'PAYMENT_FAILED' | 'CANCELLED'

export interface Order {
  id: string
  customerEmail: string
  amount: number
  status: OrderStatus
  createdAt: string
}

export interface OrderEventLog {
  eventType: string
  message: string
  occurredAt: string
}

export interface PagedOrders {
  content: Order[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ServiceHealth = 'UP' | 'DOWN'

export interface ServiceStatus {
  name: string
  status: ServiceHealth
  url: string | null
}

export interface AuthUser {
  email: string
  accessToken: string
}
