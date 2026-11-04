import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'

export function ForgotPasswordPage() {
  const [message, setMessage] = useState<string>()
  const [error, setError] = useState<string>()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(undefined)
    const data = new FormData(event.currentTarget)
    try {
      const response = await apiFetch<{ message: string }>(
        '/api/public/auth/password-reset/request',
        {
          method: 'POST',
          body: { email: data.get('email') },
        },
      )
      setMessage(response.message)
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Could not reach the server. Please try again.',
      )
    }
  }

  return (
    <main aria-labelledby="forgot-password-title">
      <section className="fl-card">
        <h1 id="forgot-password-title">Reset your password</h1>
        <p className="fl-hint">
          Enter your account email and we will send a one-time link that expires after one hour.
        </p>

        {message ? (
          <div className="fl-alert fl-alert--success" role="status">
            {message}
          </div>
        ) : (
          <form onSubmit={submit}>
            {error && (
              <div className="fl-alert fl-alert--danger" role="alert">
                {error}
              </div>
            )}
            <div className="fl-field">
              <label className="fl-label" htmlFor="reset-email">
                Email
              </label>
              <input
                className="fl-input"
                id="reset-email"
                name="email"
                type="email"
                autoComplete="email"
                required
              />
            </div>
            <button className="fl-btn fl-btn--primary fl-btn--block" type="submit">
              Send reset link
            </button>
          </form>
        )}

        <p>
          <Link className="fl-link" to="/login">
            Back to log in
          </Link>
        </p>
      </section>
    </main>
  )
}
