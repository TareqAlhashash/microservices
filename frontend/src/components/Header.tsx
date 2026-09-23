import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

export function Header() {
  const { user, logout } = useAuth()
  const { itemCount } = useCart()

  return (
    <header className="flex items-center justify-between border-b border-slate-200 bg-white px-16 py-5">
      <Link to="/" className="text-xl font-bold text-indigo-600">
        InvestorBook Store
      </Link>
      <div className="flex items-center gap-4">
        {user && (
          <Link to="/dashboard" className="text-sm font-medium text-slate-500 hover:text-slate-900">
            Dashboard
          </Link>
        )}
        <Link
          to="/cart"
          className="rounded-full bg-indigo-50 px-4 py-2 text-sm font-medium text-indigo-600"
        >
          Cart ({itemCount})
        </Link>
        {user ? (
          <div className="flex items-center gap-4">
            <span className="text-sm font-medium text-slate-500">{user.email}</span>
            <button
              onClick={logout}
              className="rounded-lg px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              Log Out
            </button>
          </div>
        ) : (
          <Link
            to="/login"
            className="rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700"
          >
            Log In
          </Link>
        )}
      </div>
    </header>
  )
}
