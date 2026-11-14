import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  acceptCreatorCampaign,
  rejectCreatorCampaign,
  submitCreatorCounterOffer,
  escrowLabel,
  getCreatorCampaign,
  PLATFORMS,
  DELIVERABLE_TYPES,
  type Campaign,
  type Platform,
  type DeliverableType,
} from '../api/campaigns'
import { ApiError } from '../api/client'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { LOCALE } from '../lib/locale'
import { formatUSD } from '../lib/money'
import { parseUSDToCents } from '../lib/moneyInput'
import { timeLeftText } from '../lib/timeLeft'
import { isAwaitingPayment, showsEscrow } from '../api/payments'
import { CampaignStatusChip } from '../brand/CampaignStatusChip'
import '../brand/campaigns.css'

interface DeliverableRow {
  platform: Platform
  type: DeliverableType
  count: string
}

const when = new Intl.DateTimeFormat(LOCALE, {
  dateStyle: 'medium',
  timeStyle: 'short',
})
const day = new Intl.DateTimeFormat(LOCALE, {
  dateStyle: 'medium',
  timeZone: 'UTC',
})
const dateOnly = (iso: string) => day.format(new Date(`${iso}T00:00:00Z`))

const typeLabel = (type: string) => type.charAt(0) + type.slice(1).toLowerCase()

function messageOf(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.errors?.reason ?? error.message
  }
  return 'Could not reach the server. Please try again.'
}

/** Full details and response actions for a campaign request received by the creator. */
export function CreatorCampaignDetailPage() {
  const { id } = useParams()
  const campaignId = Number(id)

  const load = useCallback(() => getCreatorCampaign(campaignId), [campaignId])
  const result = useFetchWith<Campaign>(load)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [statusOverride, setStatusOverride] = useState<Campaign['status'] | null>(null)

  const [price, setPrice] = useState('')
  const [changeDeliverables, setChangeDeliverables] = useState(false)
  const [rows, setRows] = useState<DeliverableRow[]>([])

  // The brand's payment shows up here without a reload: checked every 30 seconds while it is pending.
  const [refreshed, setRefreshed] = useState<Campaign | null>(null)
  const current = refreshed ?? (result.status === 'ready' ? result.data : null)
  const awaitingPayment = current ? isAwaitingPayment(current.status) : false
  useEffect(() => {
    if (!awaitingPayment) return
    const timer = window.setInterval(() => {
      getCreatorCampaign(campaignId)
        .then(setRefreshed)
        .catch(() => undefined)
    }, 30_000)
    return () => window.clearInterval(timer)
  }, [awaitingPayment, campaignId])

  async function respond(action: 'accept' | 'reject') {
    setBusy(true)
    setError(null)
    setMessage(null)

    try {
      if (action === 'accept') {
        await acceptCreatorCampaign(campaignId)
        setStatusOverride('AWAITING_PAYMENT')
        setMessage('Campaign accepted successfully.')
      } else {
        await rejectCreatorCampaign(campaignId)
        setStatusOverride('REJECTED')
        setMessage('Campaign declined.')
      }
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  async function submitCounterOffer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setMessage(null)

    const priceText = price.trim()
    const priceCents = priceText ? parseUSDToCents(priceText) : null

    if (priceText && (priceCents === null || priceCents <= 0)) {
      setError('Enter a valid price greater than zero.')
      return
    }

    if (!priceText && !changeDeliverables) {
      setError('Change the price, deliverables, or both before submitting.')
      return
    }

    if (
      changeDeliverables &&
      (rows.length === 0 ||
        rows.some((row) => !Number.isSafeInteger(Number(row.count)) || Number(row.count) <= 0))
    ) {
      setError('Add at least one deliverable and enter a valid count for each.')
      return
    }

    setBusy(true)

    try {
      await submitCreatorCounterOffer(campaignId, {
        priceCents,
        deliverables: changeDeliverables
          ? rows.map((row) => ({
              platform: row.platform,
              type: row.type,
              count: Number(row.count),
            }))
          : null,
      })

      setStatusOverride('COUNTERED')
      setMessage('Your counter-offer was submitted successfully.')
      setPrice('')
      setChangeDeliverables(false)
      setRows([])
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  if (!Number.isSafeInteger(campaignId) || campaignId <= 0) {
    return (
      <div className="fl-alert fl-alert--danger" role="alert">
        Invalid campaign ID.
      </div>
    )
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
          <Link className="fl-btn fl-btn--outline" to="/creator/campaigns">
            Back to campaigns
          </Link>
        </div>
      </div>
    )
  }

  const latest = refreshed ?? result.data
  const campaign = {
    ...latest,
    status: statusOverride ?? latest.status,
  }

  const canRespond = campaign.status === 'AWAITING_CREATOR_RESPONSE'

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>{campaign.name}</h1>
          <p>
            <CampaignStatusChip status={campaign.status} />
          </p>
        </div>
      </header>

      {error && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>{error}</div>
        </div>
      )}

      {message && (
        <div className="fl-alert fl-alert--success" role="status">
          <Icon name="check-circle" />
          <div>{message}</div>
        </div>
      )}

      <section className="fl-card fl-section" aria-label="Campaign details">
        <dl className="fl-kv">
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

          <dt>Branches</dt>
          <dd>{campaign.branches.map((branch) => branch.name).join(', ')}</dd>

          <dt>Dates</dt>
          <dd>
            {campaign.startDate && dateOnly(campaign.startDate)} to{' '}
            {campaign.lastPostingDate && dateOnly(campaign.lastPostingDate)}
          </dd>

          <dt>Deliverables</dt>
          <dd>
            {campaign.deliverables
              .map(
                (item) => `${item.count} × ${typeLabel(item.platform)} ${item.type.toLowerCase()}`,
              )
              .join(', ')}
          </dd>

          {campaign.contentRequirements && (
            <>
              <dt>Content requirements</dt>
              <dd>{campaign.contentRequirements}</dd>
            </>
          )}

          {campaign.sentAt && (
            <>
              <dt>Received</dt>
              <dd>{when.format(new Date(campaign.sentAt))}</dd>
            </>
          )}

          {campaign.responseDeadline && campaign.status === 'AWAITING_CREATOR_RESPONSE' && (
            <>
              <dt>Respond by</dt>
              <dd>{when.format(new Date(campaign.responseDeadline))}</dd>
            </>
          )}

          {campaign.paymentDeadline && (
            <>
              <dt>Payment deadline</dt>
              <dd>{when.format(new Date(campaign.paymentDeadline))}</dd>
            </>
          )}

          {campaign.acceptedAt && (
            <>
              <dt>Accepted at</dt>
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

      {campaign.status === 'AWAITING_PAYMENT' && (
        <section className="fl-card fl-section">
          <p>
            You accepted this campaign. Waiting for the brand to pay. This page updates by itself.
          </p>
          <p>{timeLeftText(campaign.paymentDeadline, Date.now())}</p>
        </section>
      )}

      {canRespond && (
        <section className="fl-card fl-section fl-stack">
          <h2 className="fl-card__title">Respond to campaign</h2>
          <p>Choose whether to accept, decline, or propose different terms.</p>

          <div className="fl-actions">
            <button
              className="fl-btn fl-btn--primary"
              type="button"
              disabled={busy}
              onClick={() => void respond('accept')}
            >
              Accept campaign
            </button>

            <button
              className="fl-btn fl-btn--outline"
              type="button"
              disabled={busy}
              onClick={() => void respond('reject')}
            >
              Decline campaign
            </button>
          </div>

          <hr />

          <h3>Make a counter-offer</h3>
          <p>
            You can propose a different price, different deliverables, or both. Leave an option
            unchanged if you agree with the original terms.
          </p>

          <form className="fl-stack" onSubmit={submitCounterOffer}>
            <div className="fl-field">
              <label className="fl-label" htmlFor="counter-price">
                Your proposed price (USD)
              </label>
              <input
                className="fl-input"
                id="counter-price"
                type="text"
                inputMode="decimal"
                placeholder="e.g. 150.00"
                value={price}
                disabled={busy}
                onChange={(event) => setPrice(event.target.value)}
              />
            </div>

            <label>
              <input
                type="checkbox"
                checked={changeDeliverables}
                disabled={busy}
                onChange={(event) => {
                  const checked = event.target.checked
                  setChangeDeliverables(checked)
                  if (checked) {
                    setRows(
                      campaign.deliverables.map((item) => ({
                        platform: item.platform,
                        type: item.type,
                        count: String(item.count),
                      })),
                    )
                  }
                }}
              />{' '}
              I want to change the deliverables
            </label>

            {changeDeliverables && (
              <div className="fl-stack">
                {rows.map((row, index) => (
                  <div className="fl-field" key={index}>
                    <label className="fl-label" htmlFor={`counter-platform-${index}`}>
                      Platform
                    </label>
                    <select
                      className="fl-input"
                      id={`counter-platform-${index}`}
                      value={row.platform}
                      disabled={busy}
                      onChange={(event) =>
                        setRows((current) =>
                          current.map((item, i) =>
                            i === index
                              ? { ...item, platform: event.target.value as Platform }
                              : item,
                          ),
                        )
                      }
                    >
                      {PLATFORMS.map((platform) => (
                        <option key={platform} value={platform}>
                          {typeLabel(platform)}
                        </option>
                      ))}
                    </select>

                    <label className="fl-label" htmlFor={`counter-type-${index}`}>
                      Content type
                    </label>
                    <select
                      className="fl-input"
                      id={`counter-type-${index}`}
                      value={row.type}
                      disabled={busy}
                      onChange={(event) =>
                        setRows((current) =>
                          current.map((item, i) =>
                            i === index
                              ? { ...item, type: event.target.value as DeliverableType }
                              : item,
                          ),
                        )
                      }
                    >
                      {DELIVERABLE_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {typeLabel(type)}
                        </option>
                      ))}
                    </select>

                    <label className="fl-label" htmlFor={`counter-count-${index}`}>
                      Quantity
                    </label>
                    <input
                      className="fl-input"
                      id={`counter-count-${index}`}
                      type="number"
                      min="1"
                      step="1"
                      value={row.count}
                      disabled={busy}
                      onChange={(event) =>
                        setRows((current) =>
                          current.map((item, i) =>
                            i === index ? { ...item, count: event.target.value } : item,
                          ),
                        )
                      }
                    />

                    <button
                      className="fl-btn fl-btn--ghost"
                      type="button"
                      disabled={busy}
                      onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
                    >
                      Remove deliverable
                    </button>
                  </div>
                ))}

                <button
                  className="fl-btn fl-btn--outline"
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    setRows((current) => [
                      ...current,
                      {
                        platform: PLATFORMS[0],
                        type: DELIVERABLE_TYPES[0],
                        count: '1',
                      },
                    ])
                  }
                >
                  Add deliverable
                </button>
              </div>
            )}

            <button className="fl-btn fl-btn--primary" type="submit" disabled={busy}>
              {busy ? 'Submitting…' : 'Submit counter-offer'}
            </button>
          </form>
        </section>
      )}

      <div>
        <Link className="fl-btn fl-btn--outline" to="/creator/campaigns">
          Back to campaigns
        </Link>
      </div>
    </div>
  )
}
