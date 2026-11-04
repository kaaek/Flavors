
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, apiFetch } from '../api/client'
import { formatUSD } from '../lib/money'
import './CreatorReviewPage.css'

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
  if (error instanceof ApiError) {
    return error.problem.errors?.reason ?? error.message
  }

  return 'Could not reach the server. Please try again.'
}

function formatDate(date: string): string {
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(date))
}

export function CreatorReviewPage() {
  const [queue, setQueue] = useState<PendingCreator[] | null>(null)
  const [selected, setSelected] = useState<CreatorReview | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadQueue = useCallback(async () => {
    try {
      setQueue(
        await apiFetch<PendingCreator[]>('/api/admin/creators/pending'),
      )
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
      setSelected(
        await apiFetch<CreatorReview>(`/api/admin/creators/${id}`),
      )
    } catch (e) {
      setError(messageOf(e))
    }
  }

  async function approve(creator: CreatorReview) {
    setError(null)
    setMessage(null)

    try {
      await apiFetch<CreatorReview>(
        `/api/admin/creators/${creator.id}/approve`,
        { method: 'POST' },
      )

      setMessage(`${creator.displayName} is approved.`)
      setSelected(null)
      await loadQueue()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  async function reject(
    creator: CreatorReview,
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()
    setError(null)
    setMessage(null)

    const reason = String(
      new FormData(event.currentTarget).get('reason') ?? '',
    )

    try {
      await apiFetch<CreatorReview>(
        `/api/admin/creators/${creator.id}/reject`,
        {
          method: 'POST',
          body: { reason },
        },
      )

      setMessage(`${creator.displayName} is rejected.`)
      setSelected(null)
      await loadQueue()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  return (
    <main className="creator-review-page">
      <nav className="creator-admin-nav" aria-label="Admin navigation">
        <a href="/" className="creator-admin-brand" aria-label="Flavors home">
          <img src="/flavors-logo.svg" alt="Flavors" />
        </a>

        <div className="creator-admin-links">
          <a href="#approvals" aria-current="page">
            Approvals
            <span className="creator-admin-count">
              {queue?.length ?? 0}
            </span>
          </a>
          <a href="#disputes">Disputes</a>
          <a href="#payments">Payments</a>
          <a href="#users">Users</a>
          <span className="creator-admin-role">Admin</span>
        </div>
      </nav>

      <div className="creator-review-content" id="approvals">
        <header className="creator-review-header">
          <p className="creator-review-eyebrow">C · FLAVORS ADMIN</p>
          <h1>Approve who gets the verified badge</h1>
          <p>
            Review creator profiles before granting verification.
            Approval is blocked while a social account needs reconnection,
            and a rejection requires a written reason.
          </p>
        </header>

        {error && (
          <div className="creator-review-message creator-review-error" role="alert">
            {error}
          </div>
        )}

        {message && (
          <div className="creator-review-message creator-review-success" role="status">
            {message}
          </div>
        )}

        <section className="creator-review-section">
          <h2>Waiting for approval</h2>

          {queue === null ? (
            <p>Loading creator profiles...</p>
          ) : queue.length === 0 ? (
            <p className="creator-review-empty">
              No profiles are waiting for approval.
            </p>
          ) : (
            <div className="creator-review-table-wrap">
              <table className="creator-review-table">
                <thead>
                  <tr>
                    <th>Creator</th>
                    <th>Category</th>
                    <th>Location</th>
                    <th>Submitted</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>

                <tbody>
                  {queue.map((creator) => (
                    <tr
                      key={creator.id}
                      className={
                        selected?.id === creator.id
                          ? 'creator-review-row-selected'
                          : ''
                      }
                    >
                      <td>
                      <button
  type="button"
  className="creator-review-name"
  onClick={() => open(creator.id)}
>
  {creator.displayName}
</button>
                      </td>
                      <td>{creator.foodCategory}</td>
                      <td>{creator.location}</td>
                      <td>{formatDate(creator.registeredAt)}</td>
                      <td>
                        <span className="creator-status creator-status-pending">
                          Pending approval
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="fl-btn fl-btn--outline fl-btn--sm"
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
        </section>

        {selected && (
          <section className="creator-review-details" aria-label="Creator details">
            <article className="fl-card creator-review-card">
              <div className="creator-review-card-header">
                <h2>{selected.displayName}</h2>
                <span className="creator-status creator-status-pending">
                  Pending approval
                </span>
              </div>

              <dl className="creator-review-info">
                <dt>Email</dt>
                <dd>{selected.email}</dd>

                <dt>Location</dt>
                <dd>{selected.location}</dd>

                <dt>Category</dt>
                <dd>{selected.foodCategory}</dd>

                <dt>Campaign price</dt>
<dd>
  Price per campaign: {formatUSD(selected.priceMinCents)} to{' '}
  {formatUSD(selected.priceMaxCents)}
</dd>

                {selected.bio && (
                  <>
                    <dt>Bio</dt>
                    <dd>{selected.bio}</dd>
                  </>
                )}
              </dl>

              <h3 className="creator-review-subheading">Social accounts</h3>

              {selected.socialAccounts.length === 0 ? (
                <p>No social accounts found.</p>
              ) : (
                <div className="creator-social-list">
                  {selected.socialAccounts.map((account) => (
                    <div
                      className="creator-social-item"
                      key={`${account.platform}:${account.externalId}`}
                    >
                      <strong>
                        {account.platform} @{account.externalId}
                      </strong>

                      <span>
                        {account.followers === null
                          ? 'Followers not pulled yet'
                          : `${account.followers} followers`}
                      </span>

                      {account.engagementRate !== null && (
                        <span>
                          {account.engagementRate}% engagement
                        </span>
                      )}

                      {account.status === 'NEEDS_RECONNECTION' && (
                        <span className="creator-status creator-status-danger">
                          Needs reconnection
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {selected.needsReconnection && (
                <div className="fl-alert fl-alert--danger creator-review-alert" role="alert">
                  <div>
  <strong>Needs reconnection</strong>
  <p>
    A connected social account needs reconnection.
    Ask the creator to reconnect it before approval.
  </p>
</div>
                </div>
              )}

              <div className="creator-review-actions">
                <button
  className="fl-btn fl-btn--primary"
  type="button"
  aria-label="Approve"
  disabled={selected.needsReconnection}
  onClick={() => approve(selected)}
>
  Approve creator
</button>

                <form onSubmit={(event) => reject(selected, event)}>
                  <label htmlFor="creator-rejection-reason">
                    Reason for rejection
                  </label>
                  <textarea
                    id="creator-rejection-reason"
                    name="reason"
                    placeholder="Write the reason for rejecting this profile..."

                  />
                  <button
                    type="submit"
                    className="fl-btn fl-btn--danger"
                    aria-label="Reject"
                  >
                    Reject...
                  </button>
                </form>

                <button
                  type="button"
                  className="fl-btn fl-btn--outline"
                  onClick={() => {
                    setSelected(null)
                    setError(null)
                  }}
                >
                  Back to list
                </button>
              </div>
            </article>
          </section>
        )}
      </div>
    </main>
  )
}
