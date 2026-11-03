import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'

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
      <main>
        <h1>Registration sent</h1>
        <p>Your profile is pending approval. You will see the result in your account.</p>
        <Link to="/login">Continue to log in</Link>
      </main>
    )
  }

  return (
    <main>
      <h1>Register as a creator</h1>
      <form onSubmit={submit}>
        {errors.form && <p role="alert">{errors.form}</p>}
        <label>
          Name
          <input name="name" required />
        </label>
        {errors.name && <p role="alert">{errors.name}</p>}

        <label>
          Email
          <input name="email" type="email" required />
        </label>
        {errors.email && <p role="alert">{errors.email}</p>}

        <label>
          Password
          <input name="password" type="password" required minLength={8} />
        </label>
        {errors.password && <p role="alert">{errors.password}</p>}

        <label>
          Food category
          <input name="foodCategory" required />
        </label>
        {errors.foodCategory && <p role="alert">{errors.foodCategory}</p>}

        <label>
          Location (city or country)
          <input name="location" required />
        </label>
        {errors.location && <p role="alert">{errors.location}</p>}

        <label>
          Minimum price per campaign (USD)
          <input name="priceMin" type="number" min="0" step="0.01" required />
        </label>
        {errors.priceMinCents && <p role="alert">{errors.priceMinCents}</p>}

        <label>
          Maximum price per campaign (USD)
          <input name="priceMax" type="number" min="0" step="0.01" required />
        </label>
        {errors.priceMaxCents && <p role="alert">{errors.priceMaxCents}</p>}

        <label>
          Short bio (optional)
          <textarea name="bio" />
        </label>

        <fieldset>
          <legend>Social account</legend>
          <label>
            Instagram username
            <input name="socialHandle" />
          </label>
          {errors.socialAccounts && <p role="alert">{errors.socialAccounts}</p>}
        </fieldset>

        <button type="submit" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send registration'}
        </button>
      </form>
    </main>
  )
}
