import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { apiFetch } from '../api/client'
import type { Role } from '../auth/authContext'
import { homeByRole } from '../auth/homeByRole'
import { LogoutButton } from '../auth/LogoutButton'
import { useMe } from '../auth/useMe'
import { navItems, roleLabel } from './navItems'

/**
 * How many creators wait for approval, for the badge on the admin's Approvals link. Fetched again whenever the page
 * changes, so approving someone updates it. A failure just hides the badge: it is a hint, not data to act on.
 */
function usePendingCreators(enabled: boolean) {
  const [count, setCount] = useState<number | null>(null)
  const { pathname } = useLocation()
  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    apiFetch<unknown[]>('/api/admin/creators/pending')
      .then((list) => !cancelled && setCount(list.length))
      .catch(() => !cancelled && setCount(null))
    return () => {
      cancelled = true
    }
  }, [enabled, pathname])
  return count
}

/**
 * The frame around every logged-in page: the top bar of the user's role (design/screens.html) and the page itself.
 * It is mounted once per role area in App.tsx, inside RequireRole, so the bar is only ever shown to the right role.
 */
export function AppShell({ role }: { role: Role }) {
  const { state } = useMe()
  const pending = usePendingCreators(role === 'ADMIN')
  const email = state.status === 'authenticated' ? state.me.email : ''

  return (
    <>
      <a className="fl-skip" href="#main">
        Skip to content
      </a>
      <nav className="fl-nav" aria-label="Main">
        <Link className="fl-nav__brand" to={homeByRole[role]} aria-label="Flavors home">
          <img src="/flavors-logo-dark.svg" alt="Flavors" />
        </Link>
        {navItems[role].map((item) => (
          <NavLink key={item.to} className="fl-navlink" to={item.to} end={item.end}>
            {item.label}{' '}
            {item.badge === 'pendingCreators' && pending !== null && pending > 0 && (
              <span className="fl-count">{pending}</span>
            )}
          </NavLink>
        ))}
        <span className="fl-role">{roleLabel[role]}</span>
        {email && <span className="fl-nav__email">{email}</span>}
        <LogoutButton className="fl-navlink" />
      </nav>
      <main id="main" className="fl-page" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  )
}
