import { use } from 'react'
import { AuthContext, type AuthContextValue } from './authContext'

/** The current login state: `{ state, refresh }`. Must be used inside <AuthProvider>. */
export function useMe(): AuthContextValue {
  const value = use(AuthContext)
  if (!value) throw new Error('useMe must be used inside <AuthProvider>')
  return value
}
