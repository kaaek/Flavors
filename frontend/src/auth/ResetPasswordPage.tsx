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
    <main>
      <h1>Choose a new password</h1>
      {!token && <p role="alert">This password-reset link is invalid.</p>}
      {message ? (
        <>
          <p>{message}</p>
          <Link to="/login">Log in</Link>
        </>
      ) : (
        token && (
          <form onSubmit={submit}>
            {error && <p role="alert">{error}</p>}
            <label>
              New password
              <input name="password" type="password" autoComplete="new-password" required />
            </label>
            <button type="submit">Change password</button>
          </form>
        )
      )}
    </main>
  )
}
