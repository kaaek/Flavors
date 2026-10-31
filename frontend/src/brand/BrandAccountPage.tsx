import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, apiFetch } from '../api/client'

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
    <main>
      <h1>Brand account</h1>
      <h2>Branches</h2>
      {loading ? (
        <p>Loading branches…</p>
      ) : (
        <ul>
          {branches.map((branch) => (
            <li key={branch.id}>
              {branch.name} — {branch.location}
            </li>
          ))}
        </ul>
      )}

      <h2>Add a branch</h2>
      <form onSubmit={addBranch}>
        {errors.form && <p role="alert">{errors.form}</p>}
        <label>
          Branch name
          <input name="name" required />
        </label>
        {errors.name && <p role="alert">{errors.name}</p>}
        <label>
          Location
          <input name="location" required />
        </label>
        {errors.location && <p role="alert">{errors.location}</p>}
        <button type="submit">Add branch</button>
      </form>
    </main>
  )
}
