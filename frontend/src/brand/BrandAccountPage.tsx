import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import './brandAccount.css'

interface Branch {
  id: number
  name: string
  location: string
}

export function BrandAccountPage() {
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
      <nav className="fl-nav" aria-label="Brand">
        <Link className="fl-nav__brand" to="/brand" aria-label="Flavors brand dashboard">
          <img src="/flavors-logo-dark.svg" alt="Flavors" />
        </Link>
        <Link className="fl-navlink" to="/brand">
          Dashboard
        </Link>
        <Link className="fl-navlink" to="/brand/account" aria-current="page">
          Account
        </Link>
        <span className="fl-role">Brand</span>
      </nav>

      <main className="fl-brand-account" aria-labelledby="brand-account-title">
        <header>
          <h1 id="brand-account-title">Brand account</h1>
          <p className="fl-hint">Manage every restaurant location under this brand account.</p>
        </header>

        <section aria-labelledby="branches-title">
          <h2 id="branches-title">Branches</h2>
          {loading ? (
            <div className="fl-card" aria-busy="true" aria-label="Loading branches">
              <div className="fl-skel" style={{ height: '1.25rem', width: '40%' }} />
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

        <section aria-labelledby="add-branch-title">
          <h2 id="add-branch-title">Add a branch</h2>
          <form className="fl-card" onSubmit={addBranch}>
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
      </main>
    </>
  )
}
