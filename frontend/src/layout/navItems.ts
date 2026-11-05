import type { Role } from '../auth/authContext'
import { homeByRole } from '../auth/homeByRole'

export interface NavItem {
  label: string
  to: string
  /** Match only this exact path (the Home link), so it isn't "current" on every page below it. */
  end?: boolean
  /** A number shown next to the label, as in the admin's Approvals link. */
  badge?: 'pendingCreators'
}

/**
 * The top bar of each role. **This is the one place to add a page's link**: a new story adds its route in App.tsx and
 * one line here. Only pages that exist are listed, so no link ever leads to a dead end (the link audit test checks it).
 */
export const navItems: Record<Role, NavItem[]> = {
  CREATOR: [
    { label: 'Home', to: homeByRole.CREATOR, end: true },
    { label: 'Find brands', to: '/creator/brands' },
    { label: 'Portfolio', to: '/creator/portfolio' },
    { label: 'Social accounts', to: '/creator/social' },
    { label: 'Profile status', to: '/creator/status' },
  ],
  BRAND: [
    { label: 'Home', to: homeByRole.BRAND, end: true },
    { label: 'Find creators', to: '/brand/creators' },
    { label: 'Account', to: '/brand/account' },
  ],
  ADMIN: [
    { label: 'Home', to: homeByRole.ADMIN, end: true },
    { label: 'Approvals', to: '/admin/creators', badge: 'pendingCreators' },
  ],
}

export const roleLabel: Record<Role, string> = {
  CREATOR: 'Creator',
  BRAND: 'Brand',
  ADMIN: 'Admin',
}
