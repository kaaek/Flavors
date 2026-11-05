import { createContext } from 'react'

export type Role = 'CREATOR' | 'BRAND' | 'ADMIN'

/** What GET /api/me returns. */
export interface Me {
  id: number
  email: string
  role: Role
}

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' } // the server answered 401: nobody is logged in
  | { status: 'authenticated'; me: Me }
  | { status: 'error' } // the server could not be reached or failed; not the same as logged out

export interface AuthContextValue {
  state: AuthState
  /** Ask the server again who is logged in (call after login or logout). */
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
