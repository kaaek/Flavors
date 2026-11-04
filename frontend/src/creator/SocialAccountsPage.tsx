import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import { Icon } from '../layout/Icon'
import { AccountCard, platformName, type AccountView } from './AccountCard'

/** One connected account, as GET /api/creator/social returns it (the token is never sent). */
export interface SocialAccount extends AccountView {
  id: number
}

/**
 * What the page says when Instagram has sent the browser back. The backend redirects to
 * /creator/social?instagram=<result> (SocialAccountController).
 */
const RESULTS: Record<string, { text: string; ok: boolean }> = {
  connected: {
    text: 'Your Instagram account is connected. Your numbers now come directly from Instagram.',
    ok: true,
  },
  denied: {
    text: 'You cancelled on Instagram, so nothing was connected. You can try again.',
    ok: false,
  },
  failed: { text: "We couldn't connect your Instagram account. Please try again.", ok: false },
  expired: {
    text: 'That connection attempt has expired. Please click Connect Instagram again.',
    ok: false,
  },
  taken: { text: 'This Instagram account is already connected to another creator.', ok: false },
  unavailable: {
    text: 'Connecting Instagram is not available yet. Please try again later.',
    ok: false,
  },
}

/**
 * The creator's social accounts (EEC-33, UC-F19.4): connect Instagram, see the numbers pulled from it, and
 * disconnect. Connecting leaves the app: the browser goes to Instagram and comes back to this page.
 *
 * @param goTo how the browser leaves for Instagram; tests replace it, because jsdom can't navigate
 */
export function SocialAccountsPage({
  goTo = (url: string) => window.location.assign(url),
}: {
  goTo?: (url: string) => void
}) {
  const [params] = useSearchParams()
  const result = RESULTS[params.get('instagram') ?? '']
  const [accounts, setAccounts] = useState<SocialAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [connecting, setConnecting] = useState(false)

  useEffect(() => {
    apiFetch<SocialAccount[]>('/api/creator/social')
      .then(setAccounts)
      .catch((e: unknown) =>
        setError(e instanceof ApiError ? e.message : 'Could not load your social accounts.'),
      )
      .finally(() => setLoading(false))
  }, [])

  async function connect() {
    setError(undefined)
    setConnecting(true)
    try {
      const { authorizationUrl } = await apiFetch<{ authorizationUrl: string }>(
        '/api/creator/social/instagram/connect',
        { method: 'POST' },
      )
      goTo(authorizationUrl)
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : 'Could not start the connection. Please try again.',
      )
      setConnecting(false)
    }
  }

  async function disconnect(account: SocialAccount) {
    setError(undefined)
    try {
      await apiFetch<void>(`/api/creator/social/${account.id}`, { method: 'DELETE' })
      // The numbers stay; the account now needs reconnection (FR-F19.16).
      setAccounts((current) =>
        current.map((a) => (a.id === account.id ? { ...a, status: 'NEEDS_RECONNECTION' } : a)),
      )
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not disconnect. Please try again.')
    }
  }

  const needsReconnection = accounts.some((a) => a.status === 'NEEDS_RECONNECTION')

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Social accounts</h1>
          <p>
            Your follower and engagement numbers come directly from Instagram, so brands can trust
            them.
          </p>
        </div>
        <button
          className="fl-btn fl-btn--primary"
          type="button"
          onClick={connect}
          disabled={connecting}
        >
          <Icon name="plus" />
          {needsReconnection ? 'Reconnect Instagram' : 'Connect Instagram'}
        </button>
      </header>

      {result && (
        <div
          className={`fl-alert fl-alert--${result.ok ? 'success' : 'danger'}`}
          role={result.ok ? 'status' : 'alert'}
        >
          <Icon name={result.ok ? 'check-circle' : 'alert'} />
          <div>{result.text}</div>
        </div>
      )}
      {error && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>{error}</div>
        </div>
      )}

      {loading ? (
        <div className="fl-card" aria-busy="true">
          Loading your accounts…
        </div>
      ) : accounts.length === 0 ? (
        <div className="fl-card fl-empty">
          <img
            className="fl-empty__art-img"
            src="/illustrations/spots/empty-creators.svg"
            alt=""
            aria-hidden="true"
          />
          <h2>No account connected yet.</h2>
          <p>Connect Instagram so brands can see your real numbers.</p>
        </div>
      ) : (
        accounts.map((account) => (
          <AccountCard key={account.id} account={account}>
            {account.status === 'CONNECTED' && (
              <button
                className="fl-btn fl-btn--danger fl-btn--sm"
                type="button"
                aria-label={`Disconnect ${platformName(account.platform)}`}
                onClick={() => disconnect(account)}
              >
                Disconnect
              </button>
            )}
          </AccountCard>
        ))
      )}
    </div>
  )
}
