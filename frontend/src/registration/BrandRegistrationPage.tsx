import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'

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
      <main>
        <h1>Restaurant registered</h1>
        <p>Your brand account is ready. You can now log in.</p>
        <Link to="/login">Continue to log in</Link>
      </main>
    )
  }

  return (
    <main>
      <h1>Register your restaurant</h1>
      <form onSubmit={submit}>
        {errors.form && <p role="alert">{errors.form}</p>}
        <label>
          Business name
          <input name="businessName" required />
        </label>
        {errors.businessName && <p role="alert">{errors.businessName}</p>}

        <label>
          Contact person
          <input name="contactPerson" required />
        </label>
        {errors.contactPerson && <p role="alert">{errors.contactPerson}</p>}

        <label>
          Email
          <input name="email" type="email" required />
        </label>
        {errors.email && <p role="alert">{errors.email}</p>}

        <label>
          Phone number
          <input name="phone" type="tel" required />
        </label>
        {errors.phone && <p role="alert">{errors.phone}</p>}

        <label>
          Password
          <input name="password" type="password" required />
        </label>
        {errors.password && <p role="alert">{errors.password}</p>}

        <fieldset>
          <legend>Branches</legend>
          {errors.branches && <p role="alert">{errors.branches}</p>}
          {branches.map((branch, index) => (
            <div className="branch-fields" key={index}>
              <label>
                Branch name
                <input
                  value={branch.name}
                  onChange={(event) => updateBranch(index, 'name', event.target.value)}
                  required
                />
              </label>
              <label>
                Location
                <input
                  value={branch.location}
                  onChange={(event) => updateBranch(index, 'location', event.target.value)}
                  required
                />
              </label>
              {branches.length > 1 && (
                <button
                  type="button"
                  onClick={() => setBranches((current) => current.filter((_, i) => i !== index))}
                >
                  Remove branch
                </button>
              )}
            </div>
          ))}
          <button
            type="button"
            onClick={() => setBranches((current) => [...current, emptyBranch()])}
          >
            Add another branch
          </button>
        </fieldset>

        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create brand account'}
        </button>
      </form>
    </main>
  )
}
