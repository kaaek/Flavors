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
    <main>
      <h1>Reset your password</h1>
      {message ? (
        <p>{message}</p>
      ) : (
        <form onSubmit={submit}>
          {error && <p role="alert">{error}</p>}
          <label>
            Email
            <input name="email" type="email" autoComplete="email" required />
          </label>
          <button type="submit">Send reset link</button>
        </form>
      )}
      <p>
        <Link to="/login">Back to log in</Link>
      </p>
    </main>
  )
}
