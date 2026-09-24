import { useEffect, useState } from 'react'
import { fetchProducts } from '../api/client'
import { ProductCard } from '../components/ProductCard'
import type { Product } from '../types'

export function CatalogPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchProducts()
      .then(setProducts)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div>
      <div className="px-16 pt-12 pb-6">
        <h1 className="text-4xl font-bold text-slate-900">Shop the Marketplace</h1>
        <p className="mt-2 text-base text-slate-500">
          Fresh finds, fair prices, checked out through your own account.
        </p>
      </div>

      {loading && <p className="px-16 text-slate-500">Loading products…</p>}
      {error && <p className="px-16 text-red-600">Couldn't load products: {error}</p>}

      {!loading && !error && (
        <div className="grid grid-cols-1 gap-6 px-16 pb-16 sm:grid-cols-2 lg:grid-cols-4">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  )
}
