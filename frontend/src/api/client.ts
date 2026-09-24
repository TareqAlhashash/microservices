import type { AuthUser, Order, OrderEventLog, PagedOrders, Product, ServiceStatus } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8765'

class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json()
    if (typeof body.message === 'string') {
      return body.message
    }
  } catch {
    // response body wasn't the shared {timestamp, message, details} error shape - fall through
  }
  return `Request failed with status ${response.status}`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, init)
  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function fetchProducts(): Promise<Product[]> {
  return request<Product[]>('/order-service/products')
}

export function fetchProduct(id: number): Promise<Product> {
  return request<Product>(`/order-service/products/${id}`)
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const body = new URLSearchParams({ username, password })
  const auth = await request<{ access_token: string }>('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  return { email: username, accessToken: auth.access_token }
}

export function placeOrder(amount: number, accessToken: string): Promise<Order> {
  return request<Order>('/order-service/orders', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ amount }),
  })
}

export function fetchOrder(id: string, accessToken: string): Promise<Order> {
  return request<Order>(`/order-service/orders/${id}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
}

// The backend returns a real org.springframework.hateoas.PagedModel<OrderResponse> - a HAL
// envelope (_embedded.orders + a page metadata block), not a flat array. _embedded is omitted
// entirely (not an empty object) when there are zero results, per HAL's own convention.
interface HalOrdersPage {
  _embedded?: { orders: Order[] }
  page: { size: number; totalElements: number; totalPages: number; number: number }
}

export async function fetchOrders(
  accessToken: string,
  page: number,
  size: number,
  search: string,
): Promise<PagedOrders> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (search.trim()) {
    params.set('search', search.trim())
  }
  const raw = await request<HalOrdersPage>(`/order-service/orders?${params.toString()}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  return {
    content: raw._embedded?.orders ?? [],
    page: raw.page.number,
    size: raw.page.size,
    totalElements: raw.page.totalElements,
    totalPages: raw.page.totalPages,
  }
}

export function fetchOrderEvents(id: string, accessToken: string): Promise<OrderEventLog[]> {
  return request<OrderEventLog[]>(`/order-service/orders/${id}/events`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
}

export function fetchServiceStatuses(): Promise<ServiceStatus[]> {
  return request<ServiceStatus[]>('/dashboard/services')
}

export { ApiError }
