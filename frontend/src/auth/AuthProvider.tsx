import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, apiFetch } from '../api/client'
import { AuthContext, type AuthState, type Me } from './authContext'

/** Loads the current user once and shares it with the whole app (see useMe). */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const refresh = useCallback(async () => {
    try {
      const me = await apiFetch<Me>('/api/me')
      setState({ status: 'authenticated', me })
    } catch (e) {
      setState(
        e instanceof ApiError && e.status === 401 ? { status: 'anonymous' } : { status: 'error' },
      )
    }
  }, [])

  useEffect(() => {
    // Fetching on mount is the point of this effect; state is set after the response arrives.
    // oxlint-disable-next-line react/set-state-in-effect -- refresh() sets state only after the await
    void refresh()
  }, [refresh])

  const value = useMemo(() => ({ state, refresh }), [state, refresh])
  return <AuthContext value={value}>{children}</AuthContext>
}
