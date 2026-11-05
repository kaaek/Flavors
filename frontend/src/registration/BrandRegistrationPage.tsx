import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import './registration.css'

interface BranchInput {
  name: string
  location: string
}

interface RegistrationResponse {
  id: number
  email: string
  role: 'BRAND'
}

const emptyBranch = (): BranchInput => ({ name: '', location: '' })
/** `{ name: 'text' }` when the field has text, otherwise nothing to spread into the request. */
function optionalText(data: FormData, name: string) {
  const value = String(data.get(name) ?? '').trim()
  return value ? { [name]: value } : {}
}
const fieldClass = (error?: string) => `fl-field${error ? ' is-invalid' : ''}`

export function BrandRegistrationPage() {
  const [branches, setBranches] = useState<BranchInput[]>([emptyBranch()])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [registered, setRegistered] = useState(false)

  function updateBranch(index: number, field: keyof BranchInput, value: string) {
    setBranches((current) =>
      current.map((branch, branchIndex) =>
        branchIndex === index ? { ...branch, [field]: value } : branch,
      ),
    )
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrors({})
    setSubmitting(true)
    const data = new FormData(event.currentTarget)

    try {
      await apiFetch<RegistrationResponse>('/api/public/auth/register/brand', {
        method: 'POST',
        body: {
          businessName: data.get('businessName'),
          contactPerson: data.get('contactPerson'),
          email: data.get('email'),
          phone: data.get('phone'),
          password: data.get('password'),
          // Optional: only sent when filled in, so the API sees them as absent rather than empty.
          ...optionalText(data, 'cuisine'),
          ...optionalText(data, 'description'),
          branches,
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
          <h1 id="registration-complete-title">Restaurant registered</h1>
          <p>Your brand account is ready. You can now log in.</p>
          <Link className="fl-btn fl-btn--primary" to="/login">
            Continue to log in
          </Link>
        </section>
      </main>
    )
  }

  return (
    <main className="fl-registration" aria-labelledby="brand-registration-title">
      <Link className="fl-registration__logo" to="/" aria-label="Flavors home">
        <img src="/flavors-logo.svg" alt="Flavors" />
      </Link>
      <header className="fl-registration__intro">
        <div>
          <h1 id="brand-registration-title">Register your restaurant</h1>
          <p>Create one brand account, add your branches and start building campaigns.</p>
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

        <div className={fieldClass(errors.businessName)}>
          <label className="fl-label" htmlFor="businessName">
            Business name
          </label>
          <input
            className="fl-input"
            id="businessName"
            name="businessName"
            autoComplete="organization"
            aria-invalid={Boolean(errors.businessName)}
            aria-describedby={errors.businessName ? 'businessName-error' : undefined}
            required
          />
          {errors.businessName && (
            <span className="fl-error" id="businessName-error" role="alert">
              {errors.businessName}
            </span>
          )}
        </div>

        <div className={fieldClass(errors.contactPerson)}>
          <label className="fl-label" htmlFor="contactPerson">
            Contact person
          </label>
          <input
            className="fl-input"
            id="contactPerson"
            name="contactPerson"
            autoComplete="name"
            aria-invalid={Boolean(errors.contactPerson)}
            aria-describedby={errors.contactPerson ? 'contactPerson-error' : undefined}
            required
          />
          {errors.contactPerson && (
            <span className="fl-error" id="contactPerson-error" role="alert">
              {errors.contactPerson}
            </span>
          )}
        </div>

        <div className={fieldClass(errors.email)}>
          <label className="fl-label" htmlFor="brand-email">
            Email
          </label>
          <input
            className="fl-input"
            id="brand-email"
            name="email"
            type="email"
            autoComplete="email"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'brand-email-error' : undefined}
            required
          />
          {errors.email && (
            <span className="fl-error" id="brand-email-error" role="alert">
              {errors.email}
            </span>
          )}
        </div>

        <div className={fieldClass(errors.phone)}>
          <label className="fl-label" htmlFor="brand-phone">
            Phone number
          </label>
          <input
            className="fl-input"
            id="brand-phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            aria-invalid={Boolean(errors.phone)}
            aria-describedby={errors.phone ? 'brand-phone-error' : undefined}
            required
          />
          {errors.phone && (
            <span className="fl-error" id="brand-phone-error" role="alert">
              {errors.phone}
            </span>
          )}
        </div>

        <div className={fieldClass(errors.password)}>
          <label className="fl-label" htmlFor="brand-password">
            Password
          </label>
          <input
            className="fl-input"
            id="brand-password"
            name="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? 'brand-password-error' : undefined}
            required
          />
          {errors.password && (
            <span className="fl-error" id="brand-password-error" role="alert">
              {errors.password}
            </span>
          )}
        </div>

        <div className={fieldClass(errors.cuisine)}>
          <label className="fl-label" htmlFor="cuisine">
            Cuisine (optional)
          </label>
          <input
            className="fl-input"
            id="cuisine"
            name="cuisine"
            placeholder="e.g. Burgers"
            maxLength={50}
            aria-invalid={Boolean(errors.cuisine)}
            aria-describedby={errors.cuisine ? 'cuisine-error' : undefined}
          />
          {errors.cuisine && (
            <span className="fl-error" id="cuisine-error" role="alert">
              {errors.cuisine}
            </span>
          )}
        </div>

        <div className={fieldClass(errors.description)}>
          <label className="fl-label" htmlFor="description">
            About your restaurant (optional)
          </label>
          <textarea
            className="fl-textarea"
            id="description"
            name="description"
            aria-invalid={Boolean(errors.description)}
            aria-describedby={errors.description ? 'description-error' : undefined}
          />
          {errors.description && (
            <span className="fl-error" id="description-error" role="alert">
              {errors.description}
            </span>
          )}
        </div>

        <fieldset>
          <legend className="fl-card__title">Branches</legend>
          <p className="fl-hint">You can add more branches later from your brand account.</p>
          {errors.branches && (
            <span className="fl-error" role="alert">
              {errors.branches}
            </span>
          )}
          {branches.map((branch, index) => (
            <section className="branch-fields fl-card" key={index}>
              <h2 className="fl-card__title">Branch {index + 1}</h2>
              <div className="fl-field">
                <label className="fl-label" htmlFor={`branch-${index}-name`}>
                  Branch name
                </label>
                <input
                  className="fl-input"
                  id={`branch-${index}-name`}
                  value={branch.name}
                  onChange={(event) => updateBranch(index, 'name', event.target.value)}
                  required
                />
              </div>
              <div className="fl-field">
                <label className="fl-label" htmlFor={`branch-${index}-location`}>
                  Location
                </label>
                <input
                  className="fl-input"
                  id={`branch-${index}-location`}
                  value={branch.location}
                  onChange={(event) => updateBranch(index, 'location', event.target.value)}
                  required
                />
              </div>
              {branches.length > 1 && (
                <button
                  className="fl-btn fl-btn--danger"
                  type="button"
                  onClick={() => setBranches((current) => current.filter((_, i) => i !== index))}
                >
                  Remove branch
                </button>
              )}
            </section>
          ))}
          <button
            className="fl-btn fl-btn--outline"
            type="button"
            onClick={() => setBranches((current) => [...current, emptyBranch()])}
          >
            Add another branch
          </button>
        </fieldset>

        <button
          className="fl-btn fl-btn--primary fl-btn--lg fl-btn--block"
          type="submit"
          disabled={submitting}
          aria-busy={submitting}
        >
          {submitting ? 'Creating account…' : 'Create brand account'}
        </button>
      </form>
    </main>
  )
}
