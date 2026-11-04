import { Link, Route, Routes } from 'react-router-dom'
import { ForgotPasswordPage } from './auth/ForgotPasswordPage'
import { LoginPage } from './auth/LoginPage'
import { LogoutButton } from './auth/LogoutButton'
import { RequireRole } from './auth/RequireRole'
import { ResetPasswordPage } from './auth/ResetPasswordPage'
import './auth/authPages.css'
import { CreatorReviewPage } from './admin/CreatorReviewPage'
import { BrandAccountPage } from './brand/BrandAccountPage'
import { CreatorStatusPage } from './creator/CreatorStatusPage'
import { BrandRegistrationPage } from './registration/BrandRegistrationPage'
import { CreatorRegistrationPage } from './registration/CreatorRegistrationPage'
import { PortfolioPage } from './creator/PortfolioPage'

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
          <main className="fl-landing">
            <section className="fl-landing__copy">
              <img className="fl-landing__logo" src="/flavors-logo.svg" alt="Flavors" />
              <h1>Creator campaigns you can measure.</h1>
              <p className="fl-landing__lede">
                Connect with food creators, run campaigns and track the restaurant visits they
                produce.
              </p>
              <div className="fl-landing__actions" aria-label="Account actions">
                <Link className="fl-btn fl-btn--primary fl-btn--lg" to="/register">
                  Create an account
                </Link>
                <Link className="fl-btn fl-btn--outline fl-btn--lg" to="/login">
                  Log in
                </Link>
              </div>
            </section>
            <img
              className="fl-landing__art"
              src="/illustrations/hero.svg"
              alt=""
              aria-hidden="true"
            />
          </main>
        }
      />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/register"
        element={
          <main className="fl-auth-shell" aria-labelledby="register-title">
            <Link className="fl-auth-logo" to="/" aria-label="Flavors home">
              <img src="/flavors-logo.svg" alt="Flavors" />
            </Link>
            <section className="fl-card fl-auth-card">
              <h1 id="register-title">Join Flavors</h1>
              <p className="fl-hint">Choose the account that matches how you use Flavors.</p>
              <div className="fl-choice-grid">
                <Link
                  className="fl-btn fl-btn--primary fl-btn--lg fl-btn--block"
                  to="/register/brand"
                >
                  Register an F&amp;B brand
                </Link>
                <Link
                  className="fl-btn fl-btn--outline fl-btn--lg fl-btn--block"
                  to="/register/creator"
                >
                  Register as a creator
                </Link>
              </div>
              <p>
                Already registered?{' '}
                <Link className="fl-link" to="/login">
                  Log in
                </Link>
              </p>
            </section>
          </main>
        }
      />
      <Route path="/register/brand" element={<BrandRegistrationPage />} />
      <Route path="/register/creator" element={<CreatorRegistrationPage />} />
      <Route
        path="/s/:slug"
        element={<Placeholder title="Creator storefront" note="Public page (UC-F17.1)." />}
      />
      <Route
        path="/claim/:token"
        element={<Placeholder title="Your offer" note="Public page (UC-F10.2)." />}
      />
      <Route
        path="/creator/status"
        element={
          <RequireRole role="CREATOR">
            <CreatorStatusPage />
          </RequireRole>
        }
      />

      <Route
        path="/creator/portfolio"
        element={
          <RequireRole role="CREATOR">
            <PortfolioPage />
          </RequireRole>
        }
      />

      <Route
        path="/creator/*"
        element={
          <RequireRole role="CREATOR">
            <main>
              <h1>Creator portal</h1>
              <nav>
                <Link to="/creator/status">Your profile status</Link>
                {' · '}
                <Link to="/creator/portfolio">Manage portfolio</Link>
              </nav>
              <LogoutButton />
            </main>
          </RequireRole>
        }
      />

      <Route
        path="/brand/account"
        element={
          <RequireRole role="BRAND">
            <BrandAccountPage />
          </RequireRole>
        }
      />
      <Route
        path="/brand/*"
        element={
          <RequireRole role="BRAND">
            <main>
              <h1>Brand dashboard</h1>
              <Link to="/brand/account">Manage account and branches</Link>
              <LogoutButton />
            </main>
          </RequireRole>
        }
      />
      <Route
        path="/admin/creators"
        element={
          <RequireRole role="ADMIN">
            <CreatorReviewPage />
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
