import { useState } from 'react'
import { ApiError } from '../api/client'
import {
  MAX_CORRECTIONS,
  reviewProof,
  type CampaignProofReviews,
  type ProofDecision,
} from '../api/proofReviews'
import { Icon } from '../layout/Icon'
import { ReviewTimer } from '../proof/ReviewTimer'
import { formatDay, formatWhen } from '../proof/dates'

/** Same limit as the backend's reason column check. */
const MAX_REASON = 2000

const typeLabel = (type: string) => type.charAt(0) + type.slice(1).toLowerCase()

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.problem.errors?.reason ?? error.message
  return 'Could not reach the server. Please try again.'
}

/**
 * UC-F6.1: the brand checks the newest version against the agreement and approves it, asks for corrections (at most
 * twice) or rejects it with a reason. Only shown while the backend lists decisions, i.e. before Timer C ends.
 */
export function ProofReviewPanel({
  campaignId,
  reviews,
  creatorName,
  onDecided,
}: {
  campaignId: number
  reviews: CampaignProofReviews
  creatorName: string | null
  onDecided: () => void
}) {
  const [choice, setChoice] = useState<ProofDecision | null>(null)
  const [reason, setReason] = useState('')
  const [reasonMissing, setReasonMissing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Hide the buttons as soon as the decision is saved, not only when the reload arrives.
  const [decided, setDecided] = useState(false)

  const submissionId = reviews.latestSubmissionId
  if (decided || reviews.availableDecisions.length === 0 || submissionId === null) return null

  const who = creatorName ?? 'The creator'
  const correctionsLeft = MAX_CORRECTIONS - reviews.correctionRoundsUsed
  const canCorrect = reviews.availableDecisions.includes('CORRECTIONS_REQUESTED')
  const { agreement } = reviews

  function choose(decision: ProofDecision | null) {
    setChoice(decision)
    setReason('')
    setReasonMissing(false)
    setError(null)
  }

  async function decide() {
    if (!choice || submissionId === null) return
    const text = reason.trim()
    if (choice === 'REJECTED' && text === '') {
      setReasonMissing(true)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await reviewProof(campaignId, submissionId, { decision: choice, reason: text || null })
      setDecided(true)
      onDecided()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="fl-card fl-section fl-stack" aria-labelledby="proof-review-title">
      <h2 id="proof-review-title">Review proof</h2>
      {reviews.reviewDeadline && (
        <ReviewTimer
          deadline={reviews.reviewDeadline}
          label={`Review by ${formatWhen(reviews.reviewDeadline)}`}
        />
      )}

      <div>
        <span className="fl-label">Check it against what you agreed</span>
        <dl className="fl-kv">
          <dt>Deliverables</dt>
          <dd>
            {agreement.deliverables
              .map((d) => `${d.count} × ${typeLabel(d.platform)} ${d.type.toLowerCase()}`)
              .join(', ') || '—'}
          </dd>
          {agreement.contentRequirements && (
            <>
              <dt>Content requirements</dt>
              <dd>{agreement.contentRequirements}</dd>
            </>
          )}
          <dt>Posting dates</dt>
          <dd>
            {formatDay(agreement.startDate)} to {formatDay(agreement.lastPostingDate)}
          </dd>
        </dl>
      </div>

      {error && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>{error}</div>
        </div>
      )}

      {choice === null && (
        <>
          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              onClick={() => choose('APPROVED')}
            >
              Approve proof
            </button>
            {canCorrect && (
              <button
                className="fl-btn fl-btn--outline"
                type="button"
                onClick={() => choose('CORRECTIONS_REQUESTED')}
              >
                Request corrections
              </button>
            )}
            <button
              className="fl-btn fl-btn--danger"
              type="button"
              onClick={() => choose('REJECTED')}
            >
              Reject…
            </button>
          </div>
          <p className="fl-hint">
            {canCorrect
              ? `You can request corrections ${correctionsLeft} more ${correctionsLeft === 1 ? 'time' : 'times'}.`
              : 'You have used both correction requests, so you can approve or reject this version.'}
          </p>
        </>
      )}

      {choice === 'APPROVED' && (
        <div className="fl-confirm" role="group" aria-labelledby="approve-title">
          <h3 id="approve-title">Approve this proof?</h3>
          <p>
            This ends the review. The payment stays held in escrow; releasing it to {who} is not
            automated yet.
          </p>
          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--ghost"
              type="button"
              disabled={busy}
              onClick={() => choose(null)}
            >
              Not yet
            </button>
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              disabled={busy}
              onClick={() => void decide()}
            >
              {busy ? 'Approving…' : 'Approve proof'}
            </button>
          </div>
        </div>
      )}

      {choice === 'CORRECTIONS_REQUESTED' && (
        <div className="fl-confirm" role="group" aria-labelledby="corrections-title">
          <h3 id="corrections-title">Request corrections</h3>
          <p>
            {who} sees your note and sends a new version.
            {correctionsLeft > 1
              ? ` You can ask ${correctionsLeft - 1} more time after this.`
              : ' This is your last correction request.'}
          </p>
          <div className="fl-field">
            <label className="fl-label" htmlFor="corrections-reason">
              What should change? <small>(optional)</small>
            </label>
            <textarea
              className="fl-textarea"
              id="corrections-reason"
              maxLength={MAX_REASON}
              value={reason}
              disabled={busy}
              onChange={(event) => setReason(event.target.value)}
            />
            <span className="fl-hint">Up to {MAX_REASON} characters.</span>
          </div>
          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--ghost"
              type="button"
              disabled={busy}
              onClick={() => choose(null)}
            >
              Back
            </button>
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              disabled={busy}
              onClick={() => void decide()}
            >
              {busy ? 'Sending…' : 'Request corrections'}
            </button>
          </div>
        </div>
      )}

      {choice === 'REJECTED' && (
        <div className="fl-confirm" role="group" aria-labelledby="reject-title">
          <h3 id="reject-title">Reject this proof</h3>
          <p>
            {who} will see your reason. The payment stays held in escrow; opening a dispute is not
            available in Flavors yet.
          </p>
          <div className={`fl-field${reasonMissing ? ' is-invalid' : ''}`}>
            <label className="fl-label" htmlFor="reject-reason">
              Reason <small>(required)</small>
            </label>
            <textarea
              className="fl-textarea"
              id="reject-reason"
              maxLength={MAX_REASON}
              value={reason}
              disabled={busy}
              aria-invalid={reasonMissing}
              aria-describedby={reasonMissing ? 'reject-reason-error' : undefined}
              onChange={(event) => {
                setReason(event.target.value)
                setReasonMissing(false)
              }}
            />
            {reasonMissing && (
              <span className="fl-error" id="reject-reason-error">
                <Icon name="alert" /> Write a reason before rejecting.
              </span>
            )}
          </div>
          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--ghost"
              type="button"
              disabled={busy}
              onClick={() => choose(null)}
            >
              Back
            </button>
            <button
              className="fl-btn fl-btn--danger"
              type="button"
              disabled={busy}
              onClick={() => void decide()}
            >
              {busy ? 'Rejecting…' : 'Reject proof'}
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
