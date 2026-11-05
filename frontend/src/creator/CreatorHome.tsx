import { Link } from 'react-router-dom'
import { Icon, type IconName } from '../layout/Icon'
import { useFetch } from '../layout/useFetch'
import { LOCALE } from '../lib/locale'
import { StatusChip, type VerificationStatus } from './StatusChip'
import type { SocialAccount } from './SocialAccountsPage'

interface CreatorStatus {
  verificationStatus: VerificationStatus
  rejectionReason: string | null
}

const actions: { to: string; icon: IconName; title: string; text: string }[] = [
  {
    to: '/creator/brands',
    icon: 'search',
    title: 'Find brands',
    text: 'Search restaurant brands by cuisine or city.',
  },
  {
    to: '/creator/portfolio',
    icon: 'user',
    title: 'Manage portfolio',
    text: 'Your details, price range and featured campaigns.',
  },
  {
    to: '/creator/social',
    icon: 'chart',
    title: 'Social accounts',
    text: 'Connect Instagram so brands see real numbers.',
  },
  {
    to: '/creator/status',
    icon: 'shield',
    title: 'Profile status',
    text: 'Where your approval stands.',
  },
]

/** The creator's start page: where her profile stands, what to do next, and one click to every creator page. */
export function CreatorHome() {
  const status = useFetch<CreatorStatus>('/api/creator/status')
  const social = useFetch<SocialAccount[]>('/api/creator/social')

  const accounts = social.status === 'ready' ? social.data : []
  // An account only counts once figures were pulled from Instagram: registration also stores a typed username,
  // which has no numbers and no connection behind it.
  const pulled = accounts.filter((a) => a.status === 'CONNECTED' && a.lastPulledAt !== null)
  const followers = pulled.reduce((sum, a) => sum + (a.followers ?? 0), 0)
  const needsReconnecting = accounts.some((a) => a.status === 'NEEDS_RECONNECTION')
  const needsAttention = social.status === 'ready' && (pulled.length === 0 || needsReconnecting)

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Welcome back</h1>
          <p>Your creator profile at a glance.</p>
        </div>
        {status.status === 'ready' && <StatusChip status={status.data.verificationStatus} />}
      </header>

      {status.status === 'error' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load your profile status. Please try again.</div>
        </div>
      )}
      {status.status === 'ready' && status.data.verificationStatus === 'PENDING_APPROVAL' && (
        <div className="fl-alert fl-alert--warning">
          <Icon name="clock" />
          <div>
            <b>Your profile is waiting for approval</b>
            Brands can't find you until an admin approves it.
          </div>
        </div>
      )}
      {status.status === 'ready' && status.data.verificationStatus === 'REJECTED' && (
        <div className="fl-alert fl-alert--danger">
          <Icon name="x-circle" />
          <div>
            <b>Your profile was not approved</b>
            {status.data.rejectionReason ?? 'Open Profile status for details.'}
          </div>
        </div>
      )}
      {needsAttention && (
        <div className="fl-alert fl-alert--info">
          <Icon name="info" />
          <div>
            <b>
              {needsReconnecting
                ? 'An account needs reconnecting'
                : 'Connect your Instagram account'}
            </b>
            Brands trust numbers that come straight from Instagram.{' '}
            <Link className="fl-link" to="/creator/social">
              {needsReconnecting ? 'Reconnect it' : 'Connect Instagram'}
            </Link>
          </div>
        </div>
      )}

      <div className="fl-stats">
        <div className="fl-stat">
          <div className="fl-stat__label">Connected accounts</div>
          <div className="fl-stat__value">{social.status === 'ready' ? pulled.length : '—'}</div>
          <div className="fl-stat__note">Instagram</div>
        </div>
        <div className="fl-stat">
          <div className="fl-stat__label">Followers</div>
          <div className="fl-stat__value">
            {pulled.length > 0 ? followers.toLocaleString(LOCALE) : '—'}
          </div>
          <div className="fl-stat__note">from your connected accounts</div>
        </div>
      </div>

      <h2 className="fl-section-title">Where to next</h2>
      <div className="fl-stats fl-stats--wide">
        {actions.map((action) => (
          <Link
            key={action.to}
            className="fl-card fl-card--interactive fl-actioncard"
            to={action.to}
          >
            <Icon name={action.icon} />
            <h3>{action.title}</h3>
            <p>{action.text}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
