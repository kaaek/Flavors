import type { CampaignStatus } from './campaigns'
import { apiFetch } from './client'

/** The limits the backend enforces (EEC-46); the form checks them first so the creator gets a clear message. */
export const PROOF_LIMITS = {
  links: 10,
  files: 5,
  fileBytes: 5 * 1024 * 1024,
  types: ['image/png', 'image/jpeg', 'image/gif', 'image/webp'],
} as const

export interface ProofFile {
  id: number
  contentType: string
  sizeBytes: number
  /** The authenticated proxy, never the storage key. */
  downloadUrl: string
}

export interface ProofSubmission {
  id: number
  round: number
  status: 'DRAFT' | 'SUBMITTED'
  createdAt: string
  submittedAt: string | null
  links: string[]
  files: ProofFile[]
}

/** What GET .../proof returns. The creator's own view includes the draft; the brand's never does. */
export interface DeliveryProof {
  campaignId: number
  campaignName: string
  campaignStatus: CampaignStatus
  /** Timer C: five days after the latest final submission. */
  reviewDeadline: string | null
  submissions: ProofSubmission[]
}

/** A field left out (or null) keeps what the draft has; an empty list clears it. */
export interface ProofDraftChange {
  links?: string[] | null
  retainedFileIds?: number[] | null
}

function proofForm(change: ProofDraftChange, files: File[]): FormData {
  const form = new FormData()
  // The backend reads this part as JSON, so it needs its own application/json type.
  const proof = { links: change.links ?? null, retainedFileIds: change.retainedFileIds ?? null }
  form.append('proof', new Blob([JSON.stringify(proof)], { type: 'application/json' }))
  files.forEach((file) => form.append('files', file))
  return form
}

export const getCreatorProof = (campaignId: number) =>
  apiFetch<DeliveryProof>(`/api/creator/campaigns/${campaignId}/proof`)

export const saveProofDraft = (campaignId: number, change: ProofDraftChange, files: File[] = []) =>
  apiFetch<DeliveryProof>(`/api/creator/campaigns/${campaignId}/proof/draft`, {
    method: 'PUT',
    body: proofForm(change, files),
  })

export const submitProof = (campaignId: number, change: ProofDraftChange) =>
  apiFetch<DeliveryProof>(`/api/creator/campaigns/${campaignId}/proof/submit`, {
    method: 'POST',
    body: proofForm(change, []),
  })

export const getBrandProof = (campaignId: number) =>
  apiFetch<DeliveryProof>(`/api/brand/campaigns/${campaignId}/proof`)

const PROOF_STATUSES: readonly CampaignStatus[] = [
  'ACTIVE',
  'PROOF_SUBMITTED',
  'CORRECTIONS_REQUESTED',
  'COMPLETED',
  'DISPUTED',
]

/** Both campaign pages show the proof from the moment the work starts (the campaign is paid and ACTIVE). */
export const showsProof = (status: CampaignStatus) => PROOF_STATUSES.includes(status)

/** The submitted versions, newest round first. */
export const submittedVersions = (proof: DeliveryProof) =>
  proof.submissions
    .filter((submission) => submission.status === 'SUBMITTED')
    .sort((a, b) => b.round - a.round)
