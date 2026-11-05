import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../api/client'
import { Icon } from '../layout/Icon'
import { StatusChip, type VerificationStatus } from './StatusChip'

interface CreatorStatus {
  verificationStatus: VerificationStatus
  rejectionReason: string | null
}

type State = { kind: 'loading' } | { kind: 'error' } | { kind: 'ready'; status: CreatorStatus }

/** Which of the three steps are done, which one is happening now. */
const progress: Record<VerificationStatus, string[]> = {
  PENDING_APPROVAL: ['is-done', 'is-current', ''],
  APPROVED: ['is-done', 'is-done', 'is-done'],
  REJECTED: ['is-done', 'is-done', ''],
}

/** The creator's own approval status (FR-F20.5, TC-F20.1.4). */
export function CreatorStatusPage() {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    apiFetch<CreatorStatus>('/api/creator/status')
      .then((status) => !cancelled && setState({ kind: 'ready', status }))
      .catch(() => !cancelled && setState({ kind: 'error' }))
    return () => {
      cancelled = true
    }
  }, [])

  if (state.kind === 'loading')
    return (
      <div className="fl-card" aria-busy="true">
        Loading…
      </div>
    )
  if (state.kind === 'error')
    return (
      <div className="fl-alert fl-alert--danger" role="alert">
        <Icon name="alert" />
        <div>Could not load your status. Please try again.</div>
      </div>
    )

  const { verificationStatus, rejectionReason } = state.status
  const steps = progress[verificationStatus]
  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Your profile</h1>
          <p>Brands can find you once an admin has approved your profile.</p>
        </div>
        <StatusChip status={verificationStatus} />
      </header>

      <div className="fl-card fl-stack">
        <ol className="fl-steps" aria-label="Approval progress">
          <li className={steps[0]}>Registered</li>
          <li className={steps[1]}>Under review</li>
          <li className={steps[2]}>Approved</li>
        </ol>

        {verificationStatus === 'PENDING_APPROVAL' && (
          <div className="fl-alert fl-alert--warning">
            <Icon name="clock" />
            <div>
              <b>Pending approval</b>
              Brands can't find you until an admin approves your profile.
            </div>
          </div>
        )}
        {verificationStatus === 'APPROVED' && (
          <div className="fl-alert fl-alert--success">
            <Icon name="shield" />
            <div>
              <b>Approved</b>
              Your profile is verified.
            </div>
          </div>
        )}
        {verificationStatus === 'REJECTED' && (
          <div className="fl-alert fl-alert--danger">
            <Icon name="x-circle" />
            <div>
              <b>Your profile was not approved</b>
              {rejectionReason ? `Reason: ${rejectionReason}` : 'No reason was given.'}
            </div>
          </div>
        )}
      </div>

      <div className="fl-row">
        <Link className="fl-btn fl-btn--outline" to="/creator/portfolio">
          Manage portfolio
        </Link>
        <Link className="fl-btn fl-btn--outline" to="/creator/social">
          Social accounts
        </Link>
      </div>
    </div>
  )
}
