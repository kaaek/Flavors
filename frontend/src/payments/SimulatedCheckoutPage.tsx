import { useCallback, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  completeSimulatedCheckout,
  getSimulatedCheckout,
  type SimulatedCheckout,
} from '../api/payments'
import { ApiError } from '../api/client'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { formatUSD } from '../lib/money'

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.problem.detail ?? error.message
  return 'Could not reach the server. Please try again.'
}

/**
 * The test checkout (mock mode only; the server does not offer it in production). It stands in for the provider's
 * hosted page: the payer pays or is refused, and the result goes through the same status check as a real provider.
 * No card details are asked for here.
 */
export function SimulatedCheckoutPage() {
  const { reference = '' } = useParams()
  const navigate = useNavigate()
  const load = useCallback(() => getSimulatedCheckout(reference), [reference])
  const result = useFetchWith<SimulatedCheckout>(load)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function complete(outcome: 'PAID' | 'FAILED') {
    setBusy(true)
    setError(null)
    try {
      const after = await completeSimulatedCheckout(reference, outcome)
      const held = after.status === 'SUCCEEDED'
      navigate(`/brand/campaigns/${after.campaignId}?payment=${held ? 'held' : 'failed'}`)
    } catch (e) {
      setError(messageOf(e))
      setBusy(false)
    }
  }

  if (result.status === 'loading') {
    return (
      <main className="fl-page">
        <div className="fl-card" aria-busy="true" aria-label="Loading payment">
          <div className="fl-skel fl-skel--line" />
        </div>
      </main>
    )
  }
  if (result.status === 'error') {
    return (
      <main className="fl-page">
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>This payment link is not valid.</div>
        </div>
      </main>
    )
  }

  const checkout = result.data
  const finished = checkout.status !== 'PENDING'

  return (
    <main className="fl-page">
      <div className="fl-stack">
        <header className="fl-pagehead">
          <div>
            <h1>Test payment</h1>
            <p>Mock checkout. No money moves.</p>
          </div>
        </header>

        <section className="fl-card fl-section" aria-label="Payment details">
          <dl className="fl-kv">
            <dt>Campaign</dt>
            <dd>{checkout.campaignName}</dd>
            <dt>Amount</dt>
            <dd>{formatUSD(checkout.amountCents)}</dd>
          </dl>
        </section>

        {error && (
          <div className="fl-alert fl-alert--danger" role="alert">
            <Icon name="alert" />
            <div>{error}</div>
          </div>
        )}

        {finished ? (
          <p>
            This payment has already been {checkout.status === 'SUCCEEDED' ? 'paid' : 'refused'}.
          </p>
        ) : (
          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              disabled={busy}
              onClick={() => void complete('PAID')}
            >
              Pay with test card
            </button>
            <button
              className="fl-btn fl-btn--outline"
              type="button"
              disabled={busy}
              onClick={() => void complete('FAILED')}
            >
              Simulate a declined card
            </button>
          </div>
        )}
      </div>
    </main>
  )
}
