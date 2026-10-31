import { Link, Route, Routes } from 'react-router-dom'
import { RequireRole } from './auth/RequireRole'
import { BrandRegistrationPage } from './registration/BrandRegistrationPage'

function Placeholder({ title, note }: { title: string; note: string }) {
  return (
    <main>
      <h1>{title}</h1>
      <p>{note}</p>
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
              {' · '}
              <Link to="/register">Register</Link>
            </nav>
          </main>
        }
      />
      <Route path="/login" element={<Placeholder title="Log in" note="Arrives with UC-F19.3." />} />
      <Route
        path="/register"
        element={
          <main>
            <h1>Register</h1>
            <Link to="/register/brand">Register an F&amp;B brand</Link>
          </main>
        }
      />
      <Route path="/register/brand" element={<BrandRegistrationPage />} />
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
            <Placeholder title="Creator portal" note="Coming in later stories." />
          </RequireRole>
        }
      />
      <Route
        path="/brand/*"
        element={
          <RequireRole role="BRAND">
            <Placeholder title="Brand dashboard" note="Coming in later stories." />
          </RequireRole>
        }
      />
      <Route
        path="/admin/*"
        element={
          <RequireRole role="ADMIN">
            <Placeholder title="Admin console" note="Coming in later stories." />
          </RequireRole>
        }
      />
      <Route path="*" element={<Placeholder title="Page not found" note="Check the address." />} />
    </Routes>
  )
}
