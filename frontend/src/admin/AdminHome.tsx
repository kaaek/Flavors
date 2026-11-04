import { Link } from 'react-router-dom'
import { Icon } from '../layout/Icon'
import { useFetch } from '../layout/useFetch'

/** The admin's start page: how many creators wait for approval, and the way to the queue. */
export function AdminHome() {
  const pending = useFetch<unknown[]>('/api/admin/creators/pending')
  const count = pending.status === 'ready' ? pending.data.length : null

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Admin console</h1>
          <p>What needs a decision today.</p>
        </div>
        <Link className="fl-btn fl-btn--primary" to="/admin/creators">
          Review creators
        </Link>
      </header>

      {pending.status === 'error' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load the approval queue. Please try again.</div>
        </div>
      )}

      <div className="fl-stats">
        <div className={`fl-stat${count ? ' fl-stat--highlight' : ''}`}>
          <div className="fl-stat__label">Waiting for approval</div>
          <div className="fl-stat__value">{count ?? '—'}</div>
          <div className="fl-stat__note">{count === 0 ? 'all caught up' : 'creator profiles'}</div>
        </div>
      </div>
    </div>
  )
}
