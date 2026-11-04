import { Link } from 'react-router-dom'

export function RegisterChoicePage() {
  return (
    <main className="fl-auth-shell" aria-labelledby="register-title">
      <Link className="fl-auth-logo" to="/" aria-label="Flavors home">
        <img src="/flavors-logo.svg" alt="Flavors" />
      </Link>
      <section className="fl-card fl-auth-card">
        <h1 id="register-title">Join Flavors</h1>
        <p className="fl-hint">Choose the account that matches how you use Flavors.</p>
        <div className="fl-choice-grid">
          <Link className="fl-btn fl-btn--primary fl-btn--lg fl-btn--block" to="/register/brand">
            Register an F&amp;B brand
          </Link>
          <Link className="fl-btn fl-btn--outline fl-btn--lg fl-btn--block" to="/register/creator">
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
  )
}
