import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, apiFetch } from '../api/client'
import { formatUSD } from '../lib/money'

interface PendingCreator {
  id: number
  displayName: string
  foodCategory: string
  location: string
  registeredAt: string
}

interface SocialAccount {
  platform: string
  externalId: string
  status: 'CONNECTED' | 'NEEDS_RECONNECTION'
  followers: number | null
  engagementRate: number | null
  lastPulledAt: string | null
}

interface CreatorReview {
  id: number
  email: string
  displayName: string
  bio: string | null
  foodCategory: string
  location: string
  priceMinCents: number
  priceMaxCents: number
  verificationStatus: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
  rejectionReason: string | null
  needsReconnection: boolean
  socialAccounts: SocialAccount[]
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.problem.errors?.reason ?? error.message
  return 'Could not reach the server. Please try again.'
}

/** UC-F20.1: the admin's queue of creator profiles that wait for approval. */
export function CreatorReviewPage() {
  const [queue, setQueue] = useState<PendingCreator[] | null>(null)
  const [selected, setSelected] = useState<CreatorReview | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadQueue = useCallback(async () => {
    try {
      setQueue(await apiFetch<PendingCreator[]>('/api/admin/creators/pending'))
    } catch (e) {
      setError(messageOf(e))
    }
  }, [])

  useEffect(() => {
    void loadQueue()
  }, [loadQueue])

  async function open(id: number) {
    setError(null)
    setMessage(null)
    try {
      setSelected(await apiFetch<CreatorReview>(`/api/admin/creators/${id}`))
    } catch (e) {
      setError(messageOf(e))
    }
  }

  async function approve(creator: CreatorReview) {
    setError(null)
    try {
      await apiFetch<CreatorReview>(`/api/admin/creators/${creator.id}/approve`, { method: 'POST' })
      setMessage(`${creator.displayName} is approved.`)
      setSelected(null)
      await loadQueue()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  async function reject(creator: CreatorReview, event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const reason = String(new FormData(event.currentTarget).get('reason') ?? '')
    try {
      await apiFetch<CreatorReview>(`/api/admin/creators/${creator.id}/reject`, {
        method: 'POST',
        body: { reason },
      })
      setMessage(`${creator.displayName} is rejected.`)
      setSelected(null)
      await loadQueue()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  return (
    <div className="fl-stack">
      <h1>Creator approvals</h1>
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}

      {selected ? (
        <section>
          <h2>{selected.displayName}</h2>
          <p>{selected.email}</p>
          <p>
            {selected.foodCategory} · {selected.location}
          </p>
          <p>
            Price per campaign: {formatUSD(selected.priceMinCents)} to{' '}
            {formatUSD(selected.priceMaxCents)}
          </p>
          {selected.bio && <p>{selected.bio}</p>}

          <h3>Social accounts</h3>
          {selected.needsReconnection && (
            <p role="alert">
              Needs reconnection. Approval is blocked until the creator reconnects.
            </p>
          )}
          <ul>
            {selected.socialAccounts.map((account) => (
              <li key={`${account.platform}:${account.externalId}`}>
                {account.platform} @{account.externalId}:{' '}
                {account.followers === null
                  ? 'followers not pulled yet'
                  : `${account.followers} followers`}
                {account.engagementRate !== null && `, ${account.engagementRate}% engagement`}
                {account.status === 'NEEDS_RECONNECTION' && ' (needs reconnection)'}
              </li>
            ))}
          </ul>

          <button
            type="button"
            disabled={selected.needsReconnection}
            onClick={() => approve(selected)}
          >
            Approve
          </button>

          <form onSubmit={(event) => reject(selected, event)}>
            <label>
              Reason for rejection
              <textarea name="reason" />
            </label>
            <button type="submit">Reject</button>
          </form>
          <button type="button" onClick={() => setSelected(null)}>
            Back to the list
          </button>
        </section>
      ) : queue === null ? (
        <p>Loading…</p>
      ) : queue.length === 0 ? (
        <p>No profiles are waiting for approval.</p>
      ) : (
        <ul>
          {queue.map((creator) => (
            <li key={creator.id}>
              <button type="button" onClick={() => open(creator.id)}>
                {creator.displayName}
              </button>{' '}
              {creator.foodCategory} · {creator.location}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
