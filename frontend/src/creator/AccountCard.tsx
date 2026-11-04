import type { ReactNode } from 'react'
import { LOCALE } from '../lib/locale'

/** What the portfolio and social-accounts APIs both say about a connected account. */
export interface AccountView {
  platform: string
  status: 'CONNECTED' | 'NEEDS_RECONNECTION'
  followers: number | null
  engagementRate: number | null
  lastPulledAt: string | null
}

export function platformName(platform: string) {
  return platform.toLowerCase() === 'instagram' ? 'Instagram' : platform
}

const dateTime = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })

/**
 * One connected account as a card: its name and state, the numbers pulled from the platform (with the time of the
 * pull, FR-F1.9), and the page's actions for it (Disconnect on the social page, none on the portfolio).
 */
export function AccountCard({ account, children }: { account: AccountView; children?: ReactNode }) {
  const connected = account.status === 'CONNECTED'
  return (
    <div className="fl-card fl-stack">
      <div className="fl-row fl-row--between">
        <h3 className="fl-card__title">{platformName(account.platform)}</h3>
        <span className={`fl-chip fl-chip--${connected ? 'success' : 'danger'}`}>
          {connected ? 'Connected' : 'Needs reconnection'}
        </span>
      </div>
      <div className="fl-stats">
        <div className="fl-stat">
          <div className="fl-stat__label">Followers</div>
          <div className={`fl-stat__value${account.followers === null ? ' is-empty' : ''}`}>
            {account.followers === null ? '—' : account.followers.toLocaleString(LOCALE)}
          </div>
        </div>
        <div className="fl-stat">
          <div className="fl-stat__label">Engagement</div>
          <div className={`fl-stat__value${account.engagementRate === null ? ' is-empty' : ''}`}>
            {account.engagementRate === null ? '—' : `${account.engagementRate.toFixed(2)}%`}
          </div>
        </div>
      </div>
      <p className="fl-muted">
        Last updated{' '}
        {account.lastPulledAt === null
          ? 'not yet'
          : dateTime.format(new Date(account.lastPulledAt))}
      </p>
      {children && <div className="fl-row">{children}</div>}
    </div>
  )
}
