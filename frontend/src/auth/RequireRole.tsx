import type { ReactNode } from 'react'
import { Link, Navigate } from 'react-router-dom'
import type { Role } from './authContext'
import { homeByRole } from './homeByRole'
import { useMe } from './useMe'

/**
 * Guards a whole area of the app. The server is the real gatekeeper (it answers 401/403 on every
 * API call); this only decides what the user sees: login page if not logged in (401), a
 * "Not allowed" page (with a way back to her own home) if logged in with the wrong role (403).
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { state } = useMe()

  switch (state.status) {
    case 'loading':
      return (
        <main className="fl-page">
          <div className="fl-card" aria-busy="true">
            Loading…
          </div>
        </main>
      )
    case 'anonymous':
      return <Navigate to="/login" replace />
    case 'error':
      return (
        <main className="fl-page">
          <div className="fl-alert fl-alert--danger" role="alert">
            Could not reach the server. Please try again.
          </div>
        </main>
      )
    case 'authenticated':
      return state.me.role === role ? (
        children
      ) : (
        <main className="fl-page">
          <div className="fl-card fl-empty">
            <h1>Not allowed</h1>
            <p>This area belongs to another kind of account.</p>
            <Link className="fl-btn fl-btn--primary" to={homeByRole[state.me.role]}>
              Go to my home
            </Link>
          </div>
        </main>
      )
  }
}
