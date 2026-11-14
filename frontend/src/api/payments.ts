import { apiFetch } from './client'
import type { CampaignStatus } from './campaigns'

export type PaymentState = 'PENDING' | 'SUCCEEDED' | 'FAILED'

/** What the Pay button gets back: send the payer to checkoutUrl. */
export interface PaymentStart {
  checkoutUrl: string
  amountCents: number
  currency: string
  status: PaymentState
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

/** What the test checkout shows for one attempt. */
export interface SimulatedCheckout {
  campaignId: number
  campaignName: string
  amountCents: number
  currency: string
  status: PaymentState
}

export const getSimulatedCheckout = (reference: string) =>
  apiFetch<SimulatedCheckout>(`/api/public/payments/simulated/${encodeURIComponent(reference)}`)

/** The test checkout's buttons: PAID (the payer paid) or FAILED (the card was refused). */
export const completeSimulatedCheckout = (reference: string, outcome: 'PAID' | 'FAILED') =>
  apiFetch<SimulatedCheckout>(
    `/api/public/payments/simulated/${encodeURIComponent(reference)}/complete`,
    {
      method: 'POST',
      body: { outcome },
    },
  )

/** Campaign statuses in which the escrow can still change, so the page keeps checking. */
export const isAwaitingPayment = (status: CampaignStatus, escrow: string | null | undefined) =>
  status === 'AWAITING_PAYMENT' || escrow === 'PENDING'
