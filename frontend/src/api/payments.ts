import { apiFetch } from './client'
import type { CampaignStatus, EscrowStatus } from './campaigns'

/** What the Pay button gets back: send the payer to checkoutUrl. */
export interface PaymentStart {
  checkoutUrl: string
}

export type PayoutStatus = 'AUTHORIZED' | 'SUBMITTED' | 'UNKNOWN' | 'FAILED' | 'CONFIRMED'

export interface Escrow {
  campaignId: number
  status: EscrowStatus
  /** Original campaign payment in cents; preserved from the existing API. */
  amountCents: number
  heldAt: string | null
  releasedAt: string | null
  updatedAt: string
  feeCents: number | null
  creatorNetCents: number | null
  payoutStatus: PayoutStatus | null
  authorizationSource: 'APPROVAL' | 'TIMER_C' | null
  authorizedAt: string | null
  submittedAt: string | null
  confirmedAt: string | null
  simulated: boolean | null
  payoutAvailable: boolean
  payoutNeedsReview: boolean
}

export const getEscrow = (viewer: 'brand' | 'creator', campaignId: number) =>
  apiFetch<Escrow>(`/api/${viewer}/campaigns/${campaignId}/escrow`)

/**
 * Starts (or continues) the payment. Send a new key for each Pay click: a retry of the same click reuses its key,
 * so a network retry never opens a second charge (NF-6).
 */
export const startCampaignPayment = (campaignId: number, idempotencyKey: string) =>
  apiFetch<PaymentStart>(`/api/brand/campaigns/${campaignId}/payment`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
  })

/** Polling continues only while the campaign waits for the brand's payment. */
export const isAwaitingPayment = (status: CampaignStatus) => status === 'AWAITING_PAYMENT'

/** Keep held, frozen and terminal escrow visible; hide pending collection on never-paid expired campaigns. */
export const showsEscrow = (status: CampaignStatus, escrow: EscrowStatus | null) =>
  escrow !== null && (status === 'AWAITING_PAYMENT' || escrow !== 'PENDING')
