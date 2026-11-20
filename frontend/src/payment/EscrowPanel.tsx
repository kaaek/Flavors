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
      if (cancelled || loading || terminal || document.visibilityState === 'hidden') {
        return
      }

      loading = true

      try {
        const fresh = await getEscrow(viewer, campaignId)

        if (cancelled) return

        if (fresh?.campaignId !== campaignId || !Object.hasOwn(escrowLabel, fresh.status)) {
          throw new Error('Unexpected escrow response')
        }

        setEscrow(fresh)
        setError(false)

        // No automatic polling for frozen or terminal escrow.
        terminal =
          fresh.status === 'FROZEN' || fresh.status === 'RELEASED' || fresh.status === 'REFUNDED'

        if (fresh.status !== fallbackStatus) {
          onStatusChanged?.()
        }
      } catch {
        if (!cancelled) {
          setError(true)
        }
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

  function getProgressMessage(): string {
    if (status === 'FROZEN') {
      return 'Escrow is frozen. Funds are held pending dispute resolution.'
    }

    if (status === 'REFUNDED') {
      return 'Escrow refunded.'
    }

    if (confirmed) {
      return escrow?.simulated
        ? 'Simulated payout confirmed — escrow released.'
        : 'Payout confirmed — escrow released.'
    }

    // Escalation takes priority over normal payout status messages.
    if (escrow?.payoutNeedsReview) {
      return 'Payout requires manual review. Our team is checking its status. Funds remain held until the outcome is verified.'
    }

    if (escrow?.payoutStatus === 'AUTHORIZED') {
      return 'Release authorized — payout pending.'
    }

    if (escrow?.payoutStatus === 'SUBMITTED') {
      return 'Payout submitted — awaiting provider confirmation.'
    }

    if (escrow?.payoutStatus === 'UNKNOWN') {
      return 'Payout outcome is being checked with the provider.'
    }

    if (escrow?.payoutStatus === 'FAILED') {
      return 'Payout failed. Funds remain held while the payout status is reviewed.'
    }

    if (status === 'HELD') {
      return 'Funds held in escrow. No release operation is recorded yet.'
    }

    if (status === 'PENDING') {
      return 'Awaiting collection confirmation.'
    }
    return 'Escrow released.'
  }

  const progress = getProgressMessage()

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
            <dt>Flavors fee</dt>
            <dd>{formatUSD(escrow.feeCents)}</dd>

            <dt>Creator net amount</dt>
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

      {escrow ? (
        <p role={escrow.payoutNeedsReview ? 'alert' : 'status'}>{progress}</p>
      ) : (
        !error && <p>Loading payout details…</p>
      )}

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
