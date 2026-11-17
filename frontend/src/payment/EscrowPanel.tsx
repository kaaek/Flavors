import { useEffect, useState } from 'react'
import { escrowLabel, type EscrowStatus } from '../api/campaigns'
import { getEscrow, type Escrow } from '../api/payments'
import { formatUSD } from '../lib/money'
import { formatWhen } from '../proof/dates'

interface Props {
  campaignId: number
  viewer: 'brand' | 'creator'
  fallbackStatus: EscrowStatus
  refreshKey: string
  onStatusChanged?: () => void
}

/** Mounted per campaign/role. A late response belongs only to its original instance. */
export function EscrowPanel(props: Props) {
  return <CampaignEscrow key={`${props.viewer}:${props.campaignId}`} {...props} />
}

function CampaignEscrow({
  campaignId,
  viewer,
  fallbackStatus,
  refreshKey,
  onStatusChanged,
}: Props) {
  const [escrow, setEscrow] = useState<Escrow | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    let loading = false
    let terminal = false
    async function refresh() {
      if (cancelled || loading || terminal || document.visibilityState === 'hidden') return
      loading = true
      try {
        const fresh = await getEscrow(viewer, campaignId)
        if (cancelled) return
        if (fresh?.campaignId !== campaignId || !Object.hasOwn(escrowLabel, fresh.status)) {
          throw new Error('Unexpected escrow response')
        }
        setEscrow(fresh)
        setError(false)
        terminal = fresh.status === 'RELEASED' || fresh.status === 'REFUNDED'
        if (fresh.status !== fallbackStatus) onStatusChanged?.()
      } catch {
        if (!cancelled) setError(true)
      } finally {
        loading = false
      }
    }
    void refresh()
    const timer = window.setInterval(() => void refresh(), 30_000)
    const wake = () => void refresh()
    window.addEventListener('focus', wake)
    document.addEventListener('visibilitychange', wake)
    return () => {
      cancelled = true
      window.clearInterval(timer)
      window.removeEventListener('focus', wake)
      document.removeEventListener('visibilitychange', wake)
    }
  }, [campaignId, viewer, fallbackStatus, refreshKey, onStatusChanged])

  const status = escrow?.status ?? fallbackStatus
  const confirmed = escrow?.payoutStatus === 'CONFIRMED' && status === 'RELEASED'
  const progress =
    status === 'FROZEN'
      ? 'Escrow is FROZEN. Payment is blocked. Dispute handling will be available through the Sprint 3 EEC-54 workflow.'
      : status === 'REFUNDED'
        ? 'Escrow refunded.'
        : escrow?.payoutStatus === 'AUTHORIZED'
          ? 'Release authorized — payout pending.'
          : escrow?.payoutStatus === 'SUBMITTED'
            ? 'Payout submitted — awaiting confirmation.'
            : escrow?.payoutStatus === 'UNKNOWN'
              ? 'Payout outcome unknown — awaiting reconciliation.'
              : escrow?.payoutStatus === 'FAILED'
                ? 'Payout failed. Funds remain held; operational follow-up is required.'
                : confirmed
                  ? escrow.simulated
                    ? 'Simulated payout confirmed — escrow released.'
                    : 'Payout confirmed — escrow released.'
                  : status === 'HELD'
                    ? 'Funds held in escrow. No release operation is recorded yet.'
                    : status === 'PENDING'
                      ? 'Awaiting collection confirmation.'
                      : 'Escrow released.'

  return (
    <section className="fl-card fl-section fl-stack" aria-label="Payment and payout">
      <h2 className="fl-card__title">Payment and payout</h2>
      <dl className="fl-kv">
        <dt>Escrow</dt>
        <dd>{escrowLabel[status]}</dd>
        {escrow && (
          <>
            <dt>Original campaign payment</dt>
            <dd>{formatUSD(escrow.amountCents)}</dd>
          </>
        )}
        {escrow?.payoutStatus && escrow.feeCents !== null && escrow.creatorNetCents !== null && (
          <>
            <dt>Flavors fee (15%)</dt>
            <dd>{formatUSD(escrow.feeCents)}</dd>
            <dt>Creator net amount (85%)</dt>
            <dd>{formatUSD(escrow.creatorNetCents)}</dd>
          </>
        )}
        {escrow?.authorizedAt && (
          <>
            <dt>Release authorized</dt>
            <dd>{formatWhen(escrow.authorizedAt)}</dd>
          </>
        )}
        {escrow?.submittedAt && (
          <>
            <dt>Payout submitted</dt>
            <dd>{formatWhen(escrow.submittedAt)}</dd>
          </>
        )}
        {confirmed && escrow.confirmedAt && (
          <>
            <dt>Payout confirmed</dt>
            <dd>{formatWhen(escrow.confirmedAt)}</dd>
          </>
        )}
      </dl>
      {escrow ? <p role="status">{progress}</p> : !error && <p>Loading payout details…</p>}
      {escrow?.authorizationSource === 'TIMER_C' && (
        <p>Release was authorized automatically after the five-day proof-review window expired.</p>
      )}
      {escrow?.simulated && (
        <p className="fl-hint">Simulation only — no real money is transferred to the creator.</p>
      )}
      {status === 'HELD' && escrow?.payoutStatus && !escrow.payoutAvailable && (
        <p>Payout execution is currently unavailable. Funds remain held.</p>
      )}
      {error && (
        <p role="alert">
          Could not refresh payout details. Displayed information may be out of date.
        </p>
      )}
    </section>
  )
}
