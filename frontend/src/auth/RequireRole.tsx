import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import type { Role } from './authContext'
import { useMe } from './useMe'

/**
 * Guards a whole area of the app. The server is the real gatekeeper (it answers 401/403 on every
 * API call); this only decides what the user sees: login page if not logged in (401), a
 * "Not allowed" message if logged in with the wrong role (403).
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { state } = useMe()

  switch (state.status) {
    case 'loading':
      return (
        <main>
          <div className="fl-card" aria-busy="true">
            Loading…
          </div>
        </main>
      )
    case 'anonymous':
      return <Navigate to="/login" replace />
    case 'error':
      return (
        <main>
          <div className="fl-alert fl-alert--danger" role="alert">
            Could not reach the server. Please try again.
          </div>
        </main>
      )
    case 'authenticated':
      return state.me.role === role ? (
        children
      ) : (
        <main>
          <div className="fl-alert fl-alert--danger" role="alert">
            Not allowed
          </div>
        </main>
      )
  }
}
