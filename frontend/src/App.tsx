import { Link, Route, Routes } from 'react-router-dom'
import { ForgotPasswordPage } from './auth/ForgotPasswordPage'
import { LoginPage } from './auth/LoginPage'
import { LogoutButton } from './auth/LogoutButton'
import { RequireRole } from './auth/RequireRole'
import { ResetPasswordPage } from './auth/ResetPasswordPage'

function Placeholder({ title, note }: { title: string; note: string }) {
  return (
    <main>
      <h1>{title}</h1>
      <p>{note}</p>
    </main>
  )
}

function RoleHome({ title }: { title: string }) {
  return (
    <main>
      <h1>{title}</h1>
      <LogoutButton />
    </main>
  )
}

/**
 * Route areas follow the API areas (docs/Architecture.md §10): /creator, /brand, /admin need a role;
 * /s/:slug (storefront) and /claim/:token (diner QR page) are public. Pages are placeholders until
 * their stories arrive.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <main>
            <h1>Flavors</h1>
            <nav>
              <Link to="/login">Log in</Link>
            </nav>
          </main>
        }
      />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/s/:slug"
        element={<Placeholder title="Creator storefront" note="Public page (UC-F17.1)." />}
      />
      <Route
        path="/claim/:token"
        element={<Placeholder title="Your offer" note="Public page (UC-F10.2)." />}
      />
      <Route
        path="/creator/*"
        element={
          <RequireRole role="CREATOR">
            <RoleHome title="Creator portal" />
          </RequireRole>
        }
      />
      <Route
        path="/brand/*"
        element={
          <RequireRole role="BRAND">
            <RoleHome title="Brand dashboard" />
          </RequireRole>
        }
      />
      <Route
        path="/admin/*"
        element={
          <RequireRole role="ADMIN">
            <RoleHome title="Admin console" />
          </RequireRole>
        }
      />
      <Route path="*" element={<Placeholder title="Page not found" note="Check the address." />} />
    </Routes>
  )
}
