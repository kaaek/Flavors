import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [message, setMessage] = useState<string>()
  const [error, setError] = useState<string>()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(undefined)
    const data = new FormData(event.currentTarget)
    try {
      const response = await apiFetch<{ message: string }>(
        '/api/public/auth/password-reset/confirm',
        {
          method: 'POST',
          body: { token, password: data.get('password') },
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
    <main className="fl-auth-shell" aria-labelledby="reset-password-title">
      <Link className="fl-auth-logo" to="/" aria-label="Flavors home">
        <img src="/flavors-logo.svg" alt="Flavors" />
      </Link>
      <section className="fl-card fl-auth-card">
        <h1 id="reset-password-title">Choose a new password</h1>
        {!token && (
          <div className="fl-alert fl-alert--danger" role="alert">
            This password-reset link is invalid.
          </div>
        )}
        {message ? (
          <div className="fl-result fl-result--ok" role="status">
            <img
              className="fl-success-art"
              src="/illustrations/spots/success.svg"
              alt=""
              aria-hidden="true"
            />
            <h2 className="fl-card__title">Password changed</h2>
            <p>{message}</p>
            <Link className="fl-btn fl-btn--primary" to="/login">
              Log in
            </Link>
          </div>
        ) : (
          token && (
            <form onSubmit={submit}>
              {error && (
                <div className="fl-alert fl-alert--danger" role="alert">
                  {error}
                </div>
              )}
              <div className="fl-field">
                <label className="fl-label" htmlFor="new-password">
                  New password
                </label>
                <input
                  className="fl-input"
                  id="new-password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  minLength={8}
                  maxLength={72}
                  aria-describedby="new-password-hint"
                  required
                />
                <span className="fl-hint" id="new-password-hint">
                  Use between 8 and 72 characters.
                </span>
              </div>
              <button className="fl-btn fl-btn--primary fl-btn--block" type="submit">
                Change password
              </button>
            </form>
          )
        )}
      </section>
    </main>
  )
}
