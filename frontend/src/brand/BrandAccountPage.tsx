import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, apiFetch } from '../api/client'

interface Branch {
  id: number
  name: string
  location: string
}

/** What creators see when they search brands. Contact details are not part of it. */
interface BrandProfile {
  businessName: string
  cuisine: string | null
  description: string | null
}

export function BrandAccountPage() {
  const [profile, setProfile] = useState<BrandProfile | null>(null)
  const [profileMessage, setProfileMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [branches, setBranches] = useState<Branch[]>([])
  const [loading, setLoading] = useState(true)
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    apiFetch<Branch[]>('/api/brand/branches')
      .then(setBranches)
      .catch((error: unknown) =>
        setErrors({ form: error instanceof ApiError ? error.message : 'Could not load branches.' }),
      )
      .finally(() => setLoading(false))
  }, [])

  // A failed profile load only hides that section; the branches below stay usable.
  useEffect(() => {
    apiFetch<BrandProfile>('/api/brand/profile')
      .then(setProfile)
      .catch(() => undefined)
  }, [])

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProfileMessage(null)
    const data = new FormData(event.currentTarget)
    try {
      await apiFetch<unknown>('/api/brand/profile', {
        method: 'PATCH',
        body: {
          cuisine: String(data.get('cuisine') ?? '').trim(),
          description: String(data.get('description') ?? '').trim(),
        },
      })
      setProfileMessage({ ok: true, text: 'Profile saved.' })
    } catch (error) {
      setProfileMessage({
        ok: false,
        text: error instanceof ApiError ? error.message : 'Could not save the profile.',
      })
    }
  }

  async function addBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrors({})
    const form = event.currentTarget
    const data = new FormData(form)

    try {
      const branch = await apiFetch<Branch>('/api/brand/branches', {
        method: 'POST',
        body: { name: data.get('name'), location: data.get('location') },
      })
      setBranches((current) => [...current, branch])
      form.reset()
    } catch (error) {
      setErrors(
        error instanceof ApiError
          ? (error.problem.errors ?? { form: error.message })
          : { form: 'Could not add the branch. Please try again.' },
      )
    }
  }

  return (
    <>
      <div className="fl-stack">
        <header className="fl-pagehead">
          <div>
            <h1 id="brand-account-title">Brand account</h1>
            <p>Manage every restaurant location under this brand account.</p>
          </div>
        </header>

        {profile && (
          <section className="fl-stack" aria-labelledby="profile-title">
            <h2 className="fl-section-title" id="profile-title">
              Restaurant profile
            </h2>
            <form className="fl-card fl-stack" onSubmit={saveProfile}>
              {profileMessage && (
                <div
                  className={`fl-alert fl-alert--${profileMessage.ok ? 'success' : 'danger'}`}
                  role={profileMessage.ok ? 'status' : 'alert'}
                >
                  {profileMessage.text}
                </div>
              )}
              <p className="fl-hint">Creators see these when they search for brands.</p>
              <div className="fl-field">
                <label className="fl-label" htmlFor="profile-cuisine">
                  Cuisine
                </label>
                <input
                  className="fl-input"
                  id="profile-cuisine"
                  name="cuisine"
                  maxLength={50}
                  defaultValue={profile.cuisine ?? ''}
                />
              </div>
              <div className="fl-field">
                <label className="fl-label" htmlFor="profile-description">
                  About your restaurant
                </label>
                <textarea
                  className="fl-textarea"
                  id="profile-description"
                  name="description"
                  defaultValue={profile.description ?? ''}
                />
              </div>
              <button className="fl-btn fl-btn--primary" type="submit">
                Save profile
              </button>
            </form>
          </section>
        )}

        <section className="fl-stack" aria-labelledby="branches-title">
          <h2 className="fl-section-title" id="branches-title">
            Branches
          </h2>
          {loading ? (
            <div className="fl-card" aria-busy="true" aria-label="Loading branches">
              <div className="fl-skel fl-skel--line" />
            </div>
          ) : branches.length === 0 ? (
            <div className="fl-card fl-empty">
              <h3>No branches yet</h3>
              <p>Add the first restaurant location below.</p>
            </div>
          ) : (
            <div className="fl-tablewrap">
              <table className="fl-table">
                <thead>
                  <tr>
                    <th scope="col">Branch</th>
                    <th scope="col">Location</th>
                  </tr>
                </thead>
                <tbody>
                  {branches.map((branch) => (
                    <tr key={branch.id}>
                      <td>{branch.name}</td>
                      <td>{branch.location}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="fl-stack" aria-labelledby="add-branch-title">
          <h2 className="fl-section-title" id="add-branch-title">
            Add a branch
          </h2>
          <form className="fl-card fl-stack" onSubmit={addBranch}>
            {errors.form && (
              <div className="fl-alert fl-alert--danger" role="alert">
                {errors.form}
              </div>
            )}
            <div className={`fl-field${errors.name ? ' is-invalid' : ''}`}>
              <label className="fl-label" htmlFor="new-branch-name">
                Branch name
              </label>
              <input
                className="fl-input"
                id="new-branch-name"
                name="name"
                aria-invalid={Boolean(errors.name)}
                aria-describedby={errors.name ? 'new-branch-name-error' : undefined}
                required
              />
              {errors.name && (
                <span className="fl-error" id="new-branch-name-error" role="alert">
                  {errors.name}
                </span>
              )}
            </div>
            <div className={`fl-field${errors.location ? ' is-invalid' : ''}`}>
              <label className="fl-label" htmlFor="new-branch-location">
                Location
              </label>
              <input
                className="fl-input"
                id="new-branch-location"
                name="location"
                aria-invalid={Boolean(errors.location)}
                aria-describedby={errors.location ? 'new-branch-location-error' : undefined}
                required
              />
              {errors.location && (
                <span className="fl-error" id="new-branch-location-error" role="alert">
                  {errors.location}
                </span>
              )}
            </div>
            <button className="fl-btn fl-btn--primary" type="submit">
              Add branch
            </button>
          </form>
        </section>
      </div>
    </>
  )
}
