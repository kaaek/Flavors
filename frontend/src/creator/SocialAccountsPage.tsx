import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, apiFetch } from '../api/client'
import { LOCALE } from '../lib/locale'

/** One connected account, as GET /api/creator/social returns it (the token is never sent). */
export interface SocialAccount {
  id: number
  platform: string
  status: 'CONNECTED' | 'NEEDS_RECONNECTION'
  followers: number | null
  engagementRate: number | null
  lastPulledAt: string | null
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

function platformName(platform: string) {
  return platform === 'instagram' ? 'Instagram' : platform
}

function figures(account: SocialAccount) {
  const parts: string[] = []
  if (account.followers !== null)
    parts.push(`${account.followers.toLocaleString(LOCALE)} followers`)
  if (account.engagementRate !== null)
    parts.push(`${account.engagementRate.toFixed(2)}% engagement`)
  if (account.lastPulledAt !== null) {
    const when = new Date(account.lastPulledAt).toLocaleString(LOCALE, {
      dateStyle: 'medium',
      timeStyle: 'short',
    })
    parts.push(`last updated ${when}`)
  }
  return parts.length === 0 ? 'No numbers yet' : parts.join(' · ')
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
      <h1>Social accounts</h1>
      <p>
        Your follower and engagement numbers come directly from Instagram, so brands can trust them.
      </p>

      {result && <p role={result.ok ? 'status' : 'alert'}>{result.text}</p>}
      {error && <p role="alert">{error}</p>}

      {loading ? (
        <p>Loading your accounts…</p>
      ) : accounts.length === 0 ? (
        <p>No account connected yet.</p>
      ) : (
        <ul>
          {accounts.map((account) => (
            <li key={account.id}>
              {platformName(account.platform)} —{' '}
              {account.status === 'CONNECTED' ? 'Connected' : 'Needs reconnection'}
              <br />
              {figures(account)}
              {account.status === 'CONNECTED' && (
                <>
                  {' '}
                  <button
                    type="button"
                    aria-label={`Disconnect ${platformName(account.platform)}`}
                    onClick={() => disconnect(account)}
                  >
                    Disconnect
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      <button type="button" onClick={connect} disabled={connecting}>
        {needsReconnection ? 'Reconnect Instagram' : 'Connect Instagram'}
      </button>
    </div>
  )
}
