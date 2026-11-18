import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import {
  approveCounterOffer,
  declineCounterOffer,
  escrowLabel,
  getBrandCounterOffer,
  getCampaign,
  type Campaign,
  type CounterOffer,
} from '../api/campaigns'
import { ApiError } from '../api/client'
import { isAwaitingPayment, showsEscrow, startCampaignPayment } from '../api/payments'
import { showsProof } from '../api/proof'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { LOCALE } from '../lib/locale'
import { formatUSD } from '../lib/money'
import { redirectTo } from '../lib/redirect'
import { timeLeftText } from '../lib/timeLeft'
import { BrandProofSection } from './BrandProofSection'
import { CampaignReviewForm } from './CampaignReviewForm'
import { CampaignStatusChip } from './CampaignStatusChip'
import './campaigns.css'

const when = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })
const day = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeZone: 'UTC' })
/** A date without a time ("2030-01-10") is shown as that day, whatever the viewer's time zone. */
const dateOnly = (iso: string) => day.format(new Date(`${iso}T00:00:00Z`))

const typeLabel = (type: string) => type.charAt(0) + type.slice(1).toLowerCase()

/** A campaign as the brand sent it. Read-only: after Send the terms are what the creator was shown. */
export function CampaignDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const campaignId = Number(id)
  const load = useCallback(() => getCampaign(campaignId), [campaignId])
  const result = useFetchWith<Campaign>(load)
  const justSent = (location.state as { sent?: boolean } | null)?.sent === true
  // Set by the payment return (api PaymentCallbackController): held, received (after the window), failed or pending.
  const paymentResult = new URLSearchParams(location.search).get('payment')
  const [counterOffer, setCounterOffer] = useState<CounterOffer | null>(null)
  const [counterOfferLoading, setCounterOfferLoading] = useState(false)
  const [counterOfferError, setCounterOfferError] = useState<string | null>(null)
  const [decisionError, setDecisionError] = useState<string | null>(null)
  const [isDeciding, setIsDeciding] = useState(false)

  const campaignStatus = result.status === 'ready' ? result.data.status : null

  useEffect(() => {
    if (campaignStatus !== 'COUNTERED') {
      setCounterOffer(null)
      return
    }

    let cancelled = false
    setCounterOfferLoading(true)
    setCounterOfferError(null)

    getBrandCounterOffer(campaignId)
      .then((data) => {
        if (!cancelled) setCounterOffer(data)
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setCounterOfferError(
            error instanceof ApiError
              ? (error.problem.errors?.reason ?? error.message)
              : 'Could not load the creator counter-offer.',
          )
        }
      })
      .finally(() => {
        if (!cancelled) setCounterOfferLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [campaignId, campaignStatus])
  const [campaignOverride, setCampaignOverride] = useState<Campaign | null>(null)
  const [paying, setPaying] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)
  // An override belongs to the campaign it was loaded for: after opening another campaign, or when a late answer
  // about the previous one arrives, it no longer applies.
  const override = campaignOverride?.id === campaignId ? campaignOverride : null
  const current = override ?? (result.status === 'ready' ? result.data : null)
  const awaitingPayment = current ? isAwaitingPayment(current.status) : false

  // The provider's answer can arrive after this page opened, so the escrow is checked again every 30 seconds.
  useEffect(() => {
    if (!awaitingPayment) return
    const timer = window.setInterval(() => {
      getCampaign(campaignId)
        .then(setCampaignOverride)
        .catch(() => undefined)
    }, 30_000)
    return () => window.clearInterval(timer)
  }, [awaitingPayment, campaignId])

  /** Starts the hosted payment with a new key per click, then sends the payer to the provider's page. */
  async function pay() {
    setPaying(true)
    setPayError(null)
    try {
      const session = await startCampaignPayment(campaignId, crypto.randomUUID())
      redirectTo(session.checkoutUrl)
    } catch (error) {
      setPayError(
        error instanceof ApiError
          ? error.message
          : 'Could not start the payment. Please try again.',
      )
      setPaying(false)
      // 409: the server found the earlier attempt already paid (or the window closed). Show the campaign as it is now.
      if (error instanceof ApiError && error.status === 409) {
        getCampaign(campaignId)
          .then(setCampaignOverride)
          .catch(() => undefined)
      }
    }
  }

  async function decideCounterOffer(action: 'approve' | 'decline') {
    setIsDeciding(true)
    setDecisionError(null)

    try {
      if (action === 'approve') {
        await approveCounterOffer(campaignId)
      } else {
        await declineCounterOffer(campaignId)
      }

      const updatedCampaign = await getCampaign(campaignId)
      setCampaignOverride(updatedCampaign)
      setCounterOffer(null)
    } catch (error) {
      setDecisionError(
        error instanceof ApiError
          ? (error.problem.errors?.reason ?? error.message)
          : 'Could not process the counter-offer. Please try again.',
      )
    } finally {
      setIsDeciding(false)
    }
  }
  if (result.status === 'loading') {
    return (
      <div className="fl-card" aria-busy="true" aria-label="Loading campaign">
        <div className="fl-skel fl-skel--line" />
      </div>
    )
  }
  if (result.status === 'error') {
    return (
      <div className="fl-stack">
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load this campaign. It may not exist.</div>
        </div>
        <div>
          <Link className="fl-btn fl-btn--outline" to="/brand/campaigns">
            Back to campaigns
          </Link>
        </div>
      </div>
    )
  }

  const campaign = override ?? result.data
  // A draft is edited, not read.
  if (campaign.status === 'DRAFT')
    return <Navigate to={`/brand/campaigns/${campaign.id}/edit`} replace />

  return (
    <div className="fl-stack">
      {justSent && campaign.responseDeadline && (
        <div className="fl-alert fl-alert--success" role="status">
          <Icon name="check-circle" />
          <div>
            Sent to {campaign.creator?.displayName}. She has 3 days to respond (until{' '}
            {when.format(new Date(campaign.responseDeadline))}).
          </div>
        </div>
      )}
      <header className="fl-pagehead">
        <div>
          <h1>{campaign.name}</h1>
          <p>
            <CampaignStatusChip status={campaign.status} />
          </p>
        </div>
      </header>
      {paymentResult === 'held' && (
        <div className="fl-alert fl-alert--success" role="status">
          <Icon name="check-circle" />
          <div>Payment received. It is held in escrow and the campaign is active.</div>
        </div>
      )}
      {paymentResult === 'received' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>
            We received a payment after the payment window closed. It is not held in escrow and the
            campaign is not active. Refunds are not automated yet.
          </div>
        </div>
      )}
      {paymentResult === 'failed' && campaign.status === 'AWAITING_PAYMENT' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>
            The payment was not completed. The campaign is still awaiting payment. You can try
            again.
          </div>
        </div>
      )}
      {paymentResult === 'pending' && (
        <div className="fl-alert" role="status">
          <div>We have not received the payment confirmation yet. This page updates by itself.</div>
        </div>
      )}
      {payError && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>{payError}</div>
        </div>
      )}
      {campaign.status === 'AWAITING_PAYMENT' && (
        <section className="fl-card fl-section fl-stack" aria-label="Payment">
          <h2 className="fl-card__title">Awaiting payment</h2>
          <p>{timeLeftText(campaign.paymentDeadline, Date.now())}</p>
          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              disabled={paying || campaign.agreedPriceCents === null}
              onClick={() => void pay()}
            >
              {paying ? 'Opening payment...' : `Pay ${formatUSD(campaign.agreedPriceCents ?? 0)}`}
            </button>
          </div>
        </section>
      )}
      {campaign.status === 'COUNTERED' && (
        <section className="fl-card fl-section fl-stack">
          <h2 className="fl-card__title">Creator counter-offer</h2>

          {counterOfferLoading && <p>Loading counter-offer...</p>}

          {counterOfferError && (
            <div className="fl-alert fl-alert--danger" role="alert">
              {counterOfferError}
            </div>
          )}

          {decisionError && (
            <div className="fl-alert fl-alert--danger" role="alert">
              {decisionError}
            </div>
          )}

          {counterOffer && (
            <>
              <dl className="fl-kv">
                <dt>Proposed price</dt>
                <dd>{formatUSD(counterOffer.priceCents)}</dd>

                <dt>Proposed deliverables</dt>
                <dd>
                  {counterOffer.deliverables
                    .map(
                      (item) =>
                        `${item.count} × ${typeLabel(item.platform)} ${item.type.toLowerCase()}`,
                    )
                    .join(', ')}
                </dd>
              </dl>

              <div className="fl-actions">
                <button
                  className="fl-btn fl-btn--primary"
                  type="button"
                  disabled={isDeciding}
                  onClick={() => void decideCounterOffer('approve')}
                >
                  {isDeciding ? 'Processing...' : 'Approve counter-offer'}
                </button>

                <button
                  className="fl-btn fl-btn--outline"
                  type="button"
                  disabled={isDeciding}
                  onClick={() => void decideCounterOffer('decline')}
                >
                  {isDeciding ? 'Processing...' : 'Decline counter-offer'}
                </button>
              </div>
            </>
          )}
        </section>
      )}
      {showsProof(campaign.status) && (
        <BrandProofSection
          campaignId={campaign.id}
          creatorName={campaign.creator?.displayName ?? null}
          lastPostingDate={campaign.lastPostingDate}
          onChanged={() =>
            void getCampaign(campaign.id)
              .then(setCampaignOverride)
              .catch(() => undefined)
          }
        />
      )}
      {campaign.status === 'COMPLETED' && (
        <CampaignReviewForm
          key={campaign.id}
          campaignId={campaign.id}
        />
      )}
      <section className="fl-card fl-section" aria-label="Campaign details">
        <dl className="fl-kv">
          <dt>Creator</dt>
          <dd>{campaign.creator?.displayName}</dd>
          <dt>Branches</dt>
          <dd>{campaign.branches.map((b) => b.name).join(', ')}</dd>
          <dt>Price offered</dt>
          <dd>
            {campaign.priceOfferedCents === null ? '—' : formatUSD(campaign.priceOfferedCents)}
          </dd>
          {campaign.agreedPriceCents !== null && (
            <>
              <dt>Agreed price</dt>
              <dd>{formatUSD(campaign.agreedPriceCents)}</dd>
            </>
          )}
          <dt>Dates</dt>
          <dd>
            {campaign.startDate && dateOnly(campaign.startDate)} to{' '}
            {campaign.lastPostingDate && dateOnly(campaign.lastPostingDate)}
          </dd>
          <dt>Deliverables</dt>
          <dd>
            {campaign.deliverables
              .map((d) => `${d.count} × ${typeLabel(d.platform)} ${d.type.toLowerCase()}`)
              .join(', ')}
          </dd>
          {campaign.contentRequirements && (
            <>
              <dt>Content requirements</dt>
              <dd>{campaign.contentRequirements}</dd>
            </>
          )}
          {campaign.screenshotsRequired && (
            <>
              <dt>Screenshots</dt>
              <dd>Required with the proof</dd>
            </>
          )}
          {campaign.sentAt && (
            <>
              <dt>Sent</dt>
              <dd>{when.format(new Date(campaign.sentAt))}</dd>
            </>
          )}
          {campaign.responseDeadline && campaign.status === 'AWAITING_CREATOR_RESPONSE' && (
            <>
              <dt>Creator must answer by</dt>
              <dd>{when.format(new Date(campaign.responseDeadline))}</dd>
            </>
          )}
          {campaign.paymentDeadline && (
            <>
              <dt>Pay by</dt>
              <dd>{when.format(new Date(campaign.paymentDeadline))}</dd>
            </>
          )}
          {campaign.acceptedAt && (
            <>
              <dt>Accepted</dt>
              <dd>{when.format(new Date(campaign.acceptedAt))}</dd>
            </>
          )}
          {campaign.escrowStatus && showsEscrow(campaign.status, campaign.escrowStatus) && (
            <>
              <dt>Escrow</dt>
              <dd>{escrowLabel[campaign.escrowStatus]}</dd>
            </>
          )}
        </dl>
      </section>

      <div>
        <Link className="fl-btn fl-btn--outline" to="/brand/campaigns">
          Back to campaigns
        </Link>
      </div>
    </div>
  )
}
