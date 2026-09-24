import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import type { CartLine, Product } from '../types'

interface CartContextValue {
  lines: CartLine[]
  itemCount: number
  total: number
  addItem: (product: Product) => void
  removeItem: (productId: number) => void
  updateQuantity: (productId: number, quantity: number) => void
  clear: () => void
}

const CartContext = createContext<CartContextValue | undefined>(undefined)

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>([])

  function addItem(product: Product) {
    setLines((current) => {
      const existing = current.find((line) => line.product.id === product.id)
      if (existing) {
        return current.map((line) =>
          line.product.id === product.id ? { ...line, quantity: line.quantity + 1 } : line,
        )
      }
      return [...current, { product, quantity: 1 }]
    })
  }

  function removeItem(productId: number) {
    setLines((current) => current.filter((line) => line.product.id !== productId))
  }

  function updateQuantity(productId: number, quantity: number) {
    if (quantity < 1) {
      removeItem(productId)
      return
    }
    setLines((current) =>
      current.map((line) => (line.product.id === productId ? { ...line, quantity } : line)),
    )
  }

  function clear() {
    setLines([])
  }

  const itemCount = useMemo(() => lines.reduce((sum, line) => sum + line.quantity, 0), [lines])
  const total = useMemo(
    () => lines.reduce((sum, line) => sum + line.product.price * line.quantity, 0),
    [lines],
  )

  return (
    <CartContext.Provider value={{ lines, itemCount, total, addItem, removeItem, updateQuantity, clear }}>
      {children}
    </CartContext.Provider>
  )
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error('useCart must be used within a CartProvider')
  }
  return context
}
