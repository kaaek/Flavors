import { Link } from 'react-router-dom'
import { homeByRole } from '../auth/homeByRole'
import { useMe } from '../auth/useMe'

/** The 404: the one place besides empty states and success screens where the playful art is allowed. */
export function NotFoundPage() {
  const { state } = useMe()
  const home = state.status === 'authenticated' ? homeByRole[state.me.role] : '/'
  return (
    <div className="fl-card fl-empty">
      <img
        className="fl-empty__art-img"
        src="/illustrations/spots/not-found.svg"
        alt=""
        aria-hidden="true"
      />
      <h1>Page not found</h1>
      <p>That address doesn't lead anywhere. Check it, or go back to your home page.</p>
      <Link className="fl-btn fl-btn--primary" to={home}>
        {state.status === 'authenticated' ? 'Go to my home' : 'Go to the start page'}
      </Link>
    </div>
  )
}
