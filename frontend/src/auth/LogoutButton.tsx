import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import { useMe } from './useMe'

export function LogoutButton() {
  const { refresh } = useMe()
  const navigate = useNavigate()
  const [error, setError] = useState<string>()

  async function logout() {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' })
      await refresh()
      navigate('/login', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not log out. Please try again.')
    }
  }

  return (
    <>
      <button type="button" onClick={logout}>
        Log out
      </button>
      {error && <p role="alert">{error}</p>}
    </>
  )
}
