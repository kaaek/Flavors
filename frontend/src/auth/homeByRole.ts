import type { Role } from './authContext'

/** Where each role lands after login, and what the logo and "go home" links point to. */
export const homeByRole: Record<Role, string> = {
  CREATOR: '/creator',
  BRAND: '/brand',
  ADMIN: '/admin',
}
