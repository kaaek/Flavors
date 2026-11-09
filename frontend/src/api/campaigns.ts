import { apiFetch } from './client'

export type CampaignStatus =
  | 'DRAFT'
  | 'AWAITING_CREATOR_RESPONSE'
  | 'COUNTERED'
  | 'REJECTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'AWAITING_PAYMENT'
  | 'ACTIVE'
  | 'PROOF_SUBMITTED'
  | 'CORRECTIONS_REQUESTED'
  | 'DISPUTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REMOVED'

/** Instagram is the only platform for now (EEC-40); the types are what a brand can ask a creator to publish. */
export const PLATFORMS = ['INSTAGRAM'] as const
export const DELIVERABLE_TYPES = ['REEL', 'POST', 'STORY', 'CAROUSEL'] as const
export type Platform = (typeof PLATFORMS)[number]
export type DeliverableType = (typeof DELIVERABLE_TYPES)[number]

export interface Deliverable {
  platform: Platform
  type: DeliverableType
  count: number
}

/** What the form sends. Every field may be empty for a draft; Send needs them all except the content requirements. */
export interface CampaignInput {
  name: string
  creatorId: number | null
  branchIds: number[]
  /** The budget: what the brand offers the creator, in integer cents. */
  priceOfferedCents: number | null
  startDate: string | null
  lastPostingDate: string | null
  deliverables: { platform: string; type: string; count: number | null }[]
  contentRequirements: string | null
}

export interface Campaign {
  id: number
  name: string
  status: CampaignStatus
  creator: { id: number; displayName: string } | null
  branches: { id: number; name: string; location: string }[]
  priceOfferedCents: number | null
  startDate: string | null
  lastPostingDate: string | null
  deliverables: Deliverable[]
  contentRequirements: string | null
  sentAt: string | null
  /** Timer A: when the creator's 3 days to answer run out. */
  responseDeadline: string | null
  createdAt: string
  updatedAt: string
}

export interface CampaignSummary {
  id: number
  name: string
  status: CampaignStatus
  creatorId: number | null
  creatorName: string | null
  priceOfferedCents: number | null
  updatedAt: string
}

export interface CampaignCreator {
  id: number
  displayName: string
  foodCategory: string
  location: string
}

const base = '/api/brand/campaigns'

export const listCampaigns = () => apiFetch<CampaignSummary[]>(base)
export const getCampaign = (id: number) => apiFetch<Campaign>(`${base}/${id}`)
export const getCampaignCreator = (creatorId: number) =>
  apiFetch<CampaignCreator>(`${base}/creators/${creatorId}`)

/** Saves a new draft. */
export const createDraft = (body: CampaignInput) =>
  apiFetch<Campaign>(base, { method: 'POST', body })
/** Replaces the details of a draft. */
export const updateDraft = (id: number, body: CampaignInput) =>
  apiFetch<Campaign>(`${base}/${id}`, { method: 'PUT', body })
/** Creates a campaign and sends it to the creator in one step. */
export const sendNew = (body: CampaignInput) =>
  apiFetch<Campaign>(`${base}/send`, { method: 'POST', body })
/** Saves the details into an existing draft and sends it. */
export const sendDraft = (id: number, body: CampaignInput) =>
  apiFetch<Campaign>(`${base}/${id}/send`, { method: 'POST', body })

/** The words shown for each status; the sent ones carry the owner of the next move. */
export const statusLabel: Record<CampaignStatus, string> = {
  DRAFT: 'Draft',
  AWAITING_CREATOR_RESPONSE: 'Awaiting creator response',
  COUNTERED: 'Countered',
  REJECTED: 'Rejected',
  DECLINED: 'Declined',
  EXPIRED: 'Expired',
  AWAITING_PAYMENT: 'Awaiting payment',
  ACTIVE: 'Active',
  PROOF_SUBMITTED: 'Proof submitted',
  CORRECTIONS_REQUESTED: 'Corrections requested',
  DISPUTED: 'Disputed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  REMOVED: 'Removed',
}
