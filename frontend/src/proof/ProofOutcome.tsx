import type { CampaignStatus } from '../api/campaigns'
import { MAX_CORRECTIONS, type ProofReview } from '../api/proofReviews'
import { Icon } from '../layout/Icon'
import { formatWhen } from './dates'

/**
 * The brand's decision on the newest version shown. Actual escrow and payout progress is read separately
 * by the payment panel; approval alone never claims that the creator received money.
 */
export function ProofOutcome({
  reviews,
  latestVersionId,
  campaignStatus,
  correctionRoundsUsed,
  viewer,
  creatorName,
}: {
  reviews: ProofReview[]
  /** Matched against the versions on screen, so a version sent a moment ago never shows an older decision. */
  latestVersionId: number | null
  campaignStatus: CampaignStatus
  correctionRoundsUsed: number
  viewer: 'creator' | 'brand'
  creatorName?: string | null
}) {
  const review = reviews.find((r) => r.submissionId === latestVersionId)
  if (!review) return null
  const when = formatWhen(review.decidedAt)
  const brand = viewer === 'creator' ? 'The brand' : 'You'
  const yourProof = viewer === 'creator' ? 'your proof' : 'the proof'

  if (review.decision === 'CORRECTIONS_REQUESTED') {
    // Only meaningful while the creator is preparing the next version.
    if (campaignStatus !== 'CORRECTIONS_REQUESTED') return null
    const left = MAX_CORRECTIONS - correctionRoundsUsed
    const next =
      viewer === 'brand'
        ? `Waiting for a new version from ${creatorName ?? 'the creator'}.`
        : left > 0
          ? `Send a new version below. The brand can ask for corrections ${left} more ${left === 1 ? 'time' : 'times'}.`
          : 'Send a new version below. This is the last round: the brand can only approve or reject it.'
    return (
      <div className="fl-alert fl-alert--warning" role="status">
        <Icon name="alert" />
        <div className="fl-stack">
          <b>
            {brand} asked for corrections on {when}
          </b>
          {review.reason && <p className="fl-quote">{review.reason}</p>}
          <span>{next}</span>
        </div>
      </div>
    )
  }

  if (review.decision === 'APPROVED') {
    return (
      <div className="fl-alert fl-alert--success" role="status">
        <Icon name="check-circle" />
        <div className="fl-stack">
          <b>
            {brand} approved {yourProof} on {when}
          </b>
          <span>
            Payment release has been authorized. Check payment and payout details for confirmation.
          </span>
        </div>
      </div>
    )
  }

  return (
    <div className="fl-alert fl-alert--danger" role="status">
      <Icon name="alert" />
      <div className="fl-stack">
        <b>
          {brand} rejected {yourProof} on {when}
        </b>
        {review.reason && <p className="fl-quote">{review.reason}</p>}
        <span>
          Escrow is FROZEN and payment is blocked. Dispute handling will be available through the
          Sprint 3 EEC-54 workflow.
        </span>
      </div>
    </div>
  )
}
