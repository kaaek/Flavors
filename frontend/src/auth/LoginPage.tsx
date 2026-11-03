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
    <main>
      <h1>Log in</h1>
      <form onSubmit={submit}>
        {error && <p role="alert">{error}</p>}
        <label>
          Email
          <input name="email" type="email" autoComplete="email" required />
        </label>
        <label>
          Password
          <input name="password" type="password" autoComplete="current-password" required />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p>
        <Link to="/forgot-password">Forgot your password?</Link>
      </p>
    </main>
  )
}
