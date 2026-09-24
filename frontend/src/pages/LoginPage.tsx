import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      navigate('/cart')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Log in failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-[calc(100vh-77px)] items-center justify-center">
      <form
        onSubmit={handleSubmit}
        className="flex w-[400px] flex-col gap-5 rounded-2xl bg-white p-10 shadow-lg"
      >
        <div>
          <h2 className="text-2xl font-bold text-slate-900">Welcome back</h2>
          <p className="mt-1 text-sm text-slate-500">Log in to check out your cart</p>
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="text-[13px] font-medium text-slate-700">Email</span>
          <input
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
            className="rounded-lg border border-slate-200 px-3 py-3 text-sm outline-indigo-500"
          />
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-[13px] font-medium text-slate-700">Password</span>
          <input
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="••••••••"
            className="rounded-lg border border-slate-200 px-3 py-3 text-sm outline-indigo-500"
          />
        </label>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-indigo-600 py-3 text-[15px] font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          {submitting ? 'Logging in…' : 'Log In'}
        </button>

        <p className="text-center text-[13px] text-slate-500">
          Demo account: demo@investorbook.com / demo12345
        </p>
      </form>
    </div>
  )
}
