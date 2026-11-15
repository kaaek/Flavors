import { apiFetch } from './client'
import type { CampaignStatus, EscrowStatus } from './campaigns'

/** What the Pay button gets back: send the payer to checkoutUrl. */
export interface PaymentStart {
  checkoutUrl: string
}

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

/** The escrow block is shown while a payment can still come in, or once it is held. An expired campaign that was never paid has none. */
export const showsEscrow = (status: CampaignStatus, escrow: EscrowStatus | null) =>
  escrow !== null && (status === 'AWAITING_PAYMENT' || escrow === 'HELD')
