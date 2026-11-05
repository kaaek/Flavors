import { Route, Routes } from 'react-router-dom'
import { CreatorReviewPage } from './admin/CreatorReviewPage'
import { AdminHome } from './admin/AdminHome'
import { ForgotPasswordPage } from './auth/ForgotPasswordPage'
import { LoginPage } from './auth/LoginPage'
import { RequireRole } from './auth/RequireRole'
import { ResetPasswordPage } from './auth/ResetPasswordPage'
import type { Role } from './auth/authContext'
import './auth/authPages.css'
import { BrandAccountPage } from './brand/BrandAccountPage'
import { BrandHome } from './brand/BrandHome'
import { CreatorSearchPage } from './brand/CreatorSearchPage'
import { BrandSearchPage } from './creator/BrandSearchPage'
import { CreatorHome } from './creator/CreatorHome'
import { CreatorStatusPage } from './creator/CreatorStatusPage'
import { PortfolioPage } from './creator/PortfolioPage'
import { SocialAccountsPage } from './creator/SocialAccountsPage'
import { AppShell } from './layout/AppShell'
import './layout/layout.css'
import { LandingPage } from './pages/LandingPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PublicPlaceholder } from './pages/PublicPlaceholder'
import { RegisterChoicePage } from './pages/RegisterChoicePage'
import { BrandRegistrationPage } from './registration/BrandRegistrationPage'
import { CreatorRegistrationPage } from './registration/CreatorRegistrationPage'

/** The top bar and frame of one role's area; only that role gets past RequireRole. */
function RoleArea({ role }: { role: Role }) {
  return (
    <RequireRole role={role}>
      <AppShell role={role} />
    </RequireRole>
  )
}

/**
 * Route areas follow the API areas (docs/Architecture.md §10): /creator, /brand and /admin need a role and share
 * that role's top bar (layout/navItems.ts lists its links); the rest is public. A new page = a route here and, if it
 * belongs in the top bar, one line in navItems.ts.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/register" element={<RegisterChoicePage />} />
      <Route path="/register/brand" element={<BrandRegistrationPage />} />
      <Route path="/register/creator" element={<CreatorRegistrationPage />} />
      <Route
        path="/s/:slug"
        element={
          <PublicPlaceholder
            title="Creator storefront"
            note="This public page arrives with the storefront story."
          />
        }
      />
      <Route
        path="/claim/:token"
        element={
          <PublicPlaceholder
            title="Your offer"
            note="This public page arrives with the offer story."
          />
        }
      />

      <Route element={<RoleArea role="CREATOR" />}>
        <Route path="/creator" element={<CreatorHome />} />
        <Route path="/creator/brands" element={<BrandSearchPage />} />
        <Route path="/creator/portfolio" element={<PortfolioPage />} />
        <Route path="/creator/social" element={<SocialAccountsPage />} />
        <Route path="/creator/status" element={<CreatorStatusPage />} />
        <Route path="/creator/*" element={<NotFoundPage />} />
      </Route>

      <Route element={<RoleArea role="BRAND" />}>
        <Route path="/brand" element={<BrandHome />} />
        <Route path="/brand/creators" element={<CreatorSearchPage />} />
        <Route path="/brand/account" element={<BrandAccountPage />} />
        <Route path="/brand/*" element={<NotFoundPage />} />
      </Route>

      <Route element={<RoleArea role="ADMIN" />}>
        <Route path="/admin" element={<AdminHome />} />
        <Route path="/admin/creators" element={<CreatorReviewPage />} />
        <Route path="/admin/*" element={<NotFoundPage />} />
      </Route>

      <Route
        path="*"
        element={
          <main className="fl-page">
            <NotFoundPage />
          </main>
        }
      />
    </Routes>
  )
}
