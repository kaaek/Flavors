import { useState, type FormEvent, type InputHTMLAttributes } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import './registration.css'

interface RegistrationResponse {
  id: number
  email: string
  role: 'CREATOR'
  verificationStatus: 'PENDING_APPROVAL'
}

// SPIKE-1 (EEC-28) chose Instagram. The real "connect" button (OAuth) arrives with EEC-33; until then
// the creator types her handle, and the follower numbers stay empty until the platform pull fills them.
const PLATFORM = 'instagram'

/** "12.50" dollars to 1250 cents. Returns undefined for an empty or invalid amount. */
function dollarsToCents(value: FormDataEntryValue | null): number | undefined {
  const text = String(value ?? '').trim()
  if (text === '' || Number.isNaN(Number(text))) return undefined
  return Math.round(Number(text) * 100)
}

export function CreatorRegistrationPage() {
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [registered, setRegistered] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrors({})
    setSubmitting(true)
    const data = new FormData(event.currentTarget)
    const handle = String(data.get('socialHandle') ?? '').trim()

    try {
      await apiFetch<RegistrationResponse>('/api/public/auth/register/creator', {
        method: 'POST',
        body: {
          name: data.get('name'),
          email: data.get('email'),
          password: data.get('password'),
          foodCategory: data.get('foodCategory'),
          location: data.get('location'),
          priceMinCents: dollarsToCents(data.get('priceMin')),
          priceMaxCents: dollarsToCents(data.get('priceMax')),
          bio: data.get('bio'),
          // No handle means no connected account: the server then refuses the registration (EX-F19.1.3).
          socialAccounts: handle ? [{ platform: PLATFORM, externalId: handle }] : [],
        },
      })
      setRegistered(true)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.problem.errors ?? { form: error.message })
      } else {
        setErrors({ form: 'Could not reach the server. Please try again.' })
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (registered) {
    return (
      <main className="fl-registration__success" aria-labelledby="registration-complete-title">
        <Link className="fl-registration__logo" to="/" aria-label="Flavors home">
          <img src="/flavors-logo.svg" alt="Flavors" />
        </Link>
        <section className="fl-card fl-empty">
          <img
            className="fl-registration__success-art"
            src="/illustrations/spots/success.svg"
            alt=""
            aria-hidden="true"
          />
          <h1 id="registration-complete-title">Registration sent</h1>
          <p>Your profile is pending approval. You will see the result in your account.</p>
          <Link className="fl-btn fl-btn--primary" to="/login">
            Continue to log in
          </Link>
        </section>
      </main>
    )
  }

  return (
    <main className="fl-registration" aria-labelledby="creator-registration-title">
      <Link className="fl-registration__logo" to="/" aria-label="Flavors home">
        <img src="/flavors-logo.svg" alt="Flavors" />
      </Link>
      <header className="fl-registration__intro">
        <div>
          <h1 id="creator-registration-title">Register as a creator</h1>
          <p>Tell brands who you are. An admin approves your profile before brands can find you.</p>
        </div>
        <img
          className="fl-registration__art"
          src="/illustrations/hero.svg"
          alt=""
          aria-hidden="true"
        />
      </header>

      <form className="fl-card fl-registration__form" onSubmit={submit}>
        {errors.form && (
          <div className="fl-alert fl-alert--danger" role="alert">
            <div>
              <b>We could not create your account.</b>
              {errors.form}
            </div>
          </div>
        )}

        <Field id="creator-name" name="name" label="Name" error={errors.name} autoComplete="name" />
        <Field
          id="creator-email"
          name="email"
          label="Email"
          type="email"
          error={errors.email}
          autoComplete="email"
        />
        <Field
          id="creator-password"
          name="password"
          label="Password"
          type="password"
          error={errors.password}
          autoComplete="new-password"
          minLength={8}
        />
        <Field
          id="creator-category"
          name="foodCategory"
          label="Food category"
          error={errors.foodCategory}
        />
        <Field
          id="creator-location"
          name="location"
          label="Location (city or country)"
          error={errors.location}
        />
        <Field
          id="creator-min"
          name="priceMin"
          label="Minimum price per campaign (USD)"
          type="number"
          prefix="$"
          error={errors.priceMinCents}
          min="0"
          step="0.01"
        />
        <Field
          id="creator-max"
          name="priceMax"
          label="Maximum price per campaign (USD)"
          type="number"
          prefix="$"
          error={errors.priceMaxCents}
          min="0"
          step="0.01"
        />

        <div className="fl-field">
          <label className="fl-label" htmlFor="creator-bio">
            Short bio <small>(optional)</small>
          </label>
          <textarea className="fl-textarea" id="creator-bio" name="bio" />
        </div>

        <fieldset>
          <legend>Social account</legend>
          <Field
            id="creator-instagram"
            name="socialHandle"
            label="Instagram username"
            required={false}
            error={errors.socialAccounts}
            hint="Type your username for now. You connect the account itself, so brands see real numbers, from Social accounts once you are logged in."
          />
        </fieldset>

        <button
          className="fl-btn fl-btn--primary fl-btn--lg fl-btn--block"
          type="submit"
          disabled={submitting}
        >
          {submitting ? 'Sending…' : 'Send registration'}
        </button>
      </form>
    </main>
  )
}

/** One labelled field with its error and hint, in the design's markup (components.html). */
function Field({
  id,
  name,
  label,
  error,
  hint,
  prefix,
  required = true,
  ...input
}: {
  id: string
  name: string
  label: string
  error?: string
  hint?: string
  prefix?: string
  required?: boolean
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'name' | 'className' | 'required'>) {
  const control = (
    <input
      className="fl-input"
      id={id}
      name={name}
      required={required}
      aria-invalid={Boolean(error)}
      aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
      {...input}
    />
  )
  return (
    <div className={`fl-field${error ? ' is-invalid' : ''}`}>
      <label className="fl-label" htmlFor={id}>
        {label}
      </label>
      {prefix ? (
        <div className="fl-affix">
          <span>{prefix}</span>
          {control}
        </div>
      ) : (
        control
      )}
      {hint && !error && (
        <span className="fl-hint" id={`${id}-hint`}>
          {hint}
        </span>
      )}
      {error && (
        <span className="fl-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
