import type { CampaignStatus } from './campaigns'
import { apiFetch } from './client'

/** At most two correction requests, so at most three versions (V3 correction_rounds_used). */
export const MAX_CORRECTIONS = 2

export type ProofDecision = 'APPROVED' | 'CORRECTIONS_REQUESTED' | 'REJECTED'

/** What still has to happen after a decision; payment release and disputes are later stories (UC-F7.2/F8.1). */
export type ProofFollowUp = 'RELEASE_PAYMENT' | 'FREEZE_PAYMENT_AND_OPEN_DISPUTE' | 'RESUBMIT_PROOF'

export interface ProofReview {
  id: number
  submissionId: number
  decision: ProofDecision
  reason: string | null
  decidedAt: string
  requiredFollowUp: ProofFollowUp
}

/** The agreed terms the brand checks the proof against. */
export interface ProofReviewAgreement {
  startDate: string | null
  lastPostingDate: string | null
  contentRequirements: string | null
  /** The proof must include at least one screenshot (FR-F5.1). */
  screenshotsRequired: boolean
  deliverables: { platform: string; type: string; count: number }[]
}

export interface CampaignProofReviews {
  campaignId: number
  campaignStatus: CampaignStatus
  correctionRoundsUsed: number
  reviewDeadline: string | null
  latestSubmissionId: number | null
  agreement: ProofReviewAgreement
  /** Empty unless this is the brand, the latest version awaits a decision and Timer C is still running. */
  availableDecisions: ProofDecision[]
  reviews: ProofReview[]
}

export const getBrandProofReviews = (campaignId: number) =>
  apiFetch<CampaignProofReviews>(`/api/brand/campaigns/${campaignId}/proof/reviews`)

export const getCreatorProofReviews = (campaignId: number) =>
  apiFetch<CampaignProofReviews>(`/api/creator/campaigns/${campaignId}/proof/reviews`)

export const reviewProof = (
  campaignId: number,
  submissionId: number,
  body: { decision: ProofDecision; reason: string | null },
) =>
  apiFetch<ProofReview>(`/api/brand/campaigns/${campaignId}/proof/${submissionId}/review`, {
    method: 'POST',
    body,
  })
