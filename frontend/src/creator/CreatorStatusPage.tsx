import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'

interface CreatorStatus {
  verificationStatus: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
  rejectionReason: string | null
}

type State = { kind: 'loading' } | { kind: 'error' } | { kind: 'ready'; status: CreatorStatus }

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

  if (state.kind === 'loading') return <p>Loading…</p>
  if (state.kind === 'error')
    return <p role="alert">Could not load your status. Please try again.</p>

  const { verificationStatus, rejectionReason } = state.status
  return (
    <div className="fl-stack">
      <h1>Your profile</h1>
      {verificationStatus === 'PENDING_APPROVAL' && (
        <p>Pending approval. Brands can't find you until an admin approves your profile.</p>
      )}
      {verificationStatus === 'APPROVED' && <p>Approved. Your profile is verified.</p>}
      {verificationStatus === 'REJECTED' && (
        <>
          <p>Your profile was not approved.</p>
          {rejectionReason && <p>Reason: {rejectionReason}</p>}
        </>
      )}
    </div>
  )
}
