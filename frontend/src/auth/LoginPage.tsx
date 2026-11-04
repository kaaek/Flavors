import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import { type Me } from './authContext'
import { useMe } from './useMe'

const homeByRole = {
  CREATOR: '/creator',
  BRAND: '/brand',
  ADMIN: '/admin',
} as const

export function LoginPage() {
  const { refresh } = useMe()
  const navigate = useNavigate()
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(undefined)
    setSubmitting(true)
    const data = new FormData(event.currentTarget)

    try {
      const me = await apiFetch<Me>('/api/public/auth/login', {
        method: 'POST',
        body: { email: data.get('email'), password: data.get('password') },
      })
      await refresh()
      navigate(homeByRole[me.role], { replace: true })
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Could not reach the server. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main aria-labelledby="login-title">
      <section className="fl-card">
        <h1 id="login-title">Welcome back</h1>
        <p className="fl-hint">Log in to continue to your Flavors workspace.</p>

        <form onSubmit={submit}>
          {error && (
            <div className="fl-alert fl-alert--danger" role="alert">
              <div>
                <b>We could not log you in.</b>
                {error}
              </div>
            </div>
          )}

          <div className="fl-field">
            <label className="fl-label" htmlFor="login-email">
              Email
            </label>
            <input
              className="fl-input"
              id="login-email"
              name="email"
              type="email"
              autoComplete="email"
              required
            />
          </div>

          <div className="fl-field">
            <label className="fl-label" htmlFor="login-password">
              Password
            </label>
            <input
              className="fl-input"
              id="login-password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
          </div>

          <button
            className="fl-btn fl-btn--primary fl-btn--lg fl-btn--block"
            type="submit"
            disabled={submitting}
            aria-busy={submitting}
          >
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <p>
          <Link className="fl-link" to="/forgot-password">
            Forgot your password?
          </Link>
        </p>
      </section>
    </main>
  )
}
