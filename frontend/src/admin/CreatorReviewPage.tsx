import { Fragment, useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, apiFetch } from '../api/client'
import { Icon } from '../layout/Icon'
import { LOCALE } from '../lib/locale'
import { formatUSD } from '../lib/money'
import { StatusChip } from '../creator/StatusChip'
import { platformName } from '../creator/accountView'

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

const dateOnly = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium' })

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
      <header className="fl-pagehead">
        <div>
          <h1>Creator approvals</h1>
          <p>Approve who gets the verified badge.</p>
        </div>
        {selected && (
          <button
            className="fl-btn fl-btn--outline"
            type="button"
            onClick={() => setSelected(null)}
          >
            Back to the list
          </button>
        )}
      </header>

      {error && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>{error}</div>
        </div>
      )}
      {message && (
        <div className="fl-alert fl-alert--success" role="status">
          <Icon name="check-circle" />
          <div>{message}</div>
        </div>
      )}

      {selected ? (
        <section className="fl-card fl-stack" aria-labelledby="creator-title">
          <div className="fl-row fl-row--between">
            <h2 className="fl-card__title" id="creator-title">
              {selected.displayName}
            </h2>
            <StatusChip status={selected.verificationStatus} />
          </div>

          {selected.needsReconnection && (
            <div className="fl-alert fl-alert--danger" role="alert">
              <Icon name="alert" />
              <div>
                <b>Can't approve yet</b>A social account is disconnected, so we can't confirm the
                creator's numbers. Approval is blocked until she reconnects it.
              </div>
            </div>
          )}

          <dl className="fl-kv">
            <dt>Email</dt>
            <dd>{selected.email}</dd>
            <dt>Location</dt>
            <dd>{selected.location}</dd>
            <dt>Category</dt>
            <dd>{selected.foodCategory}</dd>
            <dt>Price range</dt>
            <dd>
              {formatUSD(selected.priceMinCents)} – {formatUSD(selected.priceMaxCents)}
            </dd>
            {selected.bio && (
              <>
                <dt>Bio</dt>
                <dd>{selected.bio}</dd>
              </>
            )}
            {selected.socialAccounts.map((account) => (
              <Fragment key={`${account.platform}:${account.externalId}`}>
                <dt>
                  {platformName(account.platform)} @{account.externalId}
                </dt>
                <dd>
                  {account.followers === null
                    ? 'followers not pulled yet'
                    : `${account.followers.toLocaleString(LOCALE)} followers`}
                  {account.engagementRate !== null && ` · ${account.engagementRate}% engagement`}{' '}
                  {account.status === 'NEEDS_RECONNECTION' && (
                    <span className="fl-chip fl-chip--danger">Needs reconnection</span>
                  )}
                </dd>
              </Fragment>
            ))}
          </dl>

          <div className="fl-row">
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              disabled={selected.needsReconnection}
              onClick={() => approve(selected)}
            >
              Approve
            </button>
          </div>

          <form className="fl-stack" onSubmit={(event) => reject(selected, event)}>
            <div className="fl-field">
              <label className="fl-label" htmlFor="reject-reason">
                Reason for rejection
              </label>
              <textarea className="fl-textarea" id="reject-reason" name="reason" />
              <span className="fl-hint">Required. The creator sees this reason.</span>
            </div>
            <div className="fl-row">
              <button className="fl-btn fl-btn--danger" type="submit">
                Reject
              </button>
            </div>
          </form>
        </section>
      ) : queue === null ? (
        <div className="fl-card" aria-busy="true">
          Loading…
        </div>
      ) : queue.length === 0 ? (
        <div className="fl-card fl-empty">
          <img
            className="fl-empty__art-img"
            src="/illustrations/spots/empty-creators.svg"
            alt=""
            aria-hidden="true"
          />
          <h2>No profiles are waiting for approval.</h2>
          <p>New creator registrations appear here.</p>
        </div>
      ) : (
        <div className="fl-tablewrap">
          <table className="fl-table">
            <thead>
              <tr>
                <th scope="col">Creator</th>
                <th scope="col">Category</th>
                <th scope="col">Location</th>
                <th scope="col">Submitted</th>
                <th scope="col">Status</th>
                <th scope="col">
                  <span className="fl-sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {queue.map((creator) => (
                <tr key={creator.id}>
                  <td>
                    <b>{creator.displayName}</b>
                  </td>
                  <td>{creator.foodCategory}</td>
                  <td>{creator.location}</td>
                  <td>{dateOnly.format(new Date(creator.registeredAt))}</td>
                  <td>
                    <StatusChip status="PENDING_APPROVAL" />
                  </td>
                  <td className="num">
                    <button
                      className="fl-btn fl-btn--outline fl-btn--sm"
                      type="button"
                      aria-label={`Review ${creator.displayName}`}
                      onClick={() => open(creator.id)}
                    >
                      Review
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
