import { Link } from 'react-router-dom'
import { Icon } from '../layout/Icon'
import { useFetch } from '../layout/useFetch'

interface Branch {
  id: number
  name: string
  location: string
}

/** The brand's start page: its branches, and an honest empty state for the campaigns that come with later stories. */
export function BrandHome() {
  const branches = useFetch<Branch[]>('/api/brand/branches')
  const list = branches.status === 'ready' ? branches.data : []

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Brand dashboard</h1>
          <p>Your restaurant account at a glance.</p>
        </div>
        <Link className="fl-btn fl-btn--outline" to="/brand/account">
          Manage branches
        </Link>
      </header>

      {branches.status === 'error' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load your branches. Please try again.</div>
        </div>
      )}

      <div className="fl-stats">
        <div className="fl-stat">
          <div className="fl-stat__label">Branches</div>
          <div className="fl-stat__value">{branches.status === 'ready' ? list.length : '—'}</div>
          <div className="fl-stat__note">restaurant locations</div>
        </div>
      </div>

      {list.length > 0 && (
        <div className="fl-tablewrap">
          <table className="fl-table">
            <thead>
              <tr>
                <th scope="col">Branch</th>
                <th scope="col">Location</th>
              </tr>
            </thead>
            <tbody>
              {list.map((branch) => (
                <tr key={branch.id}>
                  <td>{branch.name}</td>
                  <td>{branch.location}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="fl-card fl-empty">
        <img
          className="fl-empty__art-img"
          src="/illustrations/spots/empty-campaigns.svg"
          alt=""
          aria-hidden="true"
        />
        <h2>No campaigns yet</h2>
        <p>
          Creating campaigns and finding creators arrives in the next stories. Your branches are
          ready for it.
        </p>
      </div>
    </div>
  )
}
