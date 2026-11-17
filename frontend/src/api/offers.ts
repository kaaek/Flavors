import type { CampaignStatus } from './campaigns'
import { apiFetch } from './client'

/** The days an offer can be used, Monday first, as the API names them. */
export const DAYS = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const
export type Day = (typeof DAYS)[number]

/** Whether followers can use the offer right now (FR-F10.4). The server works it out on every read. */
export type OfferState = 'NOT_LIVE_YET' | 'LIVE' | 'PAUSED' | 'EXPIRED' | 'FULL' | 'ENDED'

/** What the offer form sends (UC-F10.1). Empty optional fields get the server's defaults. */
export interface OfferInput {
  discountPercent: number | null
  /** The last day the offer works ("2030-02-01"); it stops at the end of that day. */
  expiresOn: string | null
  validDays: Day[]
  /** "14:30"; both empty means all day. */
  validFrom: string | null
  validTo: string | null
  allBranches: boolean
  branchIds: number[]
  /** Empty means no minimum. */
  minPurchaseCents: number | null
  /** Empty means no maximum. */
  maxRedemptions: number | null
  /** Empty means once per person (EX-F10.1.1). */
  perPersonLimit: number | null
}

export interface Offer {
  id: number
  campaignId: number
  creatorId: number | null
  discountPercent: number
  expiresOn: string
  expiresAt: string
  validDays: Day[]
  validFrom: string | null
  validTo: string | null
  allBranches: boolean
  branchIds: number[]
  minPurchaseCents: number
  maxRedemptions: number | null
  perPersonLimit: number
  /** How many times it has been used so far. */
  redemptions: number
  state: OfferState
  /** False once the campaign is active or over: diners may already hold QR codes for it. */
  editable: boolean
  updatedAt: string
}

const path = (campaignId: number) => `/api/brand/campaigns/${campaignId}/offer`

/** The campaign's offer, or null when none has been set up yet (the server answers 204). */
export const getOffer = (campaignId: number) =>
  apiFetch<Offer | undefined>(path(campaignId)).then((offer) => offer ?? null)

/** Sets up the offer, or replaces its conditions: a campaign has one offer. */
export const saveOffer = (campaignId: number, body: OfferInput) =>
  apiFetch<Offer>(path(campaignId), { method: 'PUT', body })

/** The campaign statuses in which the brand can still set up or change the offer (until it is active). */
export const OFFER_EDITABLE: ReadonlySet<CampaignStatus> = new Set<CampaignStatus>([
  'DRAFT',
  'AWAITING_CREATOR_RESPONSE',
  'COUNTERED',
  'AWAITING_PAYMENT',
])

export const offerStateLabel: Record<OfferState, string> = {
  NOT_LIVE_YET: 'Not live yet',
  LIVE: 'Live',
  PAUSED: 'Paused',
  EXPIRED: 'Expired',
  FULL: 'Fully used',
  ENDED: 'Ended',
}

/** What each state means for the brand, shown under the chip. */
export const offerStateHint: Record<OfferState, string> = {
  NOT_LIVE_YET: 'Followers can use it once the campaign is active.',
  LIVE: 'Followers can use it now.',
  PAUSED: 'Paused while the campaign is disputed.',
  EXPIRED: 'Its expiry date has passed.',
  FULL: 'It reached its maximum number of uses.',
  ENDED: 'The campaign is over.',
}
