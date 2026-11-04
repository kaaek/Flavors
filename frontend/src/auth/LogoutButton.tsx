import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import { useMe } from './useMe'

/** @param className the button's look; the top bar passes its link style, elsewhere it is an outline button */
export function LogoutButton({ className = 'fl-btn fl-btn--outline' }: { className?: string }) {
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
      <button className={className} type="button" onClick={logout}>
        Log out
      </button>
      {error && (
        <span className="fl-error" role="alert">
          {error}
        </span>
      )}
    </>
  )
}
