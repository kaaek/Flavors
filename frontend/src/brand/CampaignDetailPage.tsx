import { useCallback } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { getCampaign, type Campaign } from '../api/campaigns'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { LOCALE } from '../lib/locale'
import { formatUSD } from '../lib/money'
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

  const campaign = result.data
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
