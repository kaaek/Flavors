import { Link, Navigate } from 'react-router-dom'
import { homeByRole } from '../auth/homeByRole'
import { useMe } from '../auth/useMe'

export function LandingPage() {
  const { state } = useMe()
  // Someone who is already logged in has no use for "Create an account": send them to their own home.
  if (state.status === 'authenticated') return <Navigate to={homeByRole[state.me.role]} replace />

  return (
    <main className="fl-landing">
      <section className="fl-landing__copy">
        <img className="fl-landing__logo" src="/flavors-logo.svg" alt="Flavors" />
        <h1>Creator campaigns you can measure.</h1>
        <p className="fl-landing__lede">
          Connect with food creators, run campaigns and track the restaurant visits they produce.
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
      <img className="fl-landing__art" src="/illustrations/hero.svg" alt="" aria-hidden="true" />
    </main>
  )
}
