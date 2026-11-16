import { Link, useParams } from 'react-router-dom'
import { useFetch } from '../layout/useFetch'
import type { CreatorPortfolio } from '../api/creatorPortfolio'
import { formatUSD } from '../lib/money'
import { LOCALE } from '../lib/locale'
import './creatorPortfolio.css'

const show = (value: string | null | undefined) => value || 'Not provided'
const number = (value: number | null) =>
  value === null ? 'Not available' : value.toLocaleString(LOCALE)
const date = (value: string | null) =>
  value ? new Date(value).toLocaleString(LOCALE) : 'Not updated yet'
const price = (min: number | null, max: number | null) =>
  min === null && max === null
    ? 'Not provided'
    : min === null
      ? formatUSD(max!)
      : max === null
        ? formatUSD(min)
        : `${formatUSD(min)} – ${formatUSD(max)}`

function PortfolioDetails({ portfolio: p }: { portfolio: CreatorPortfolio }) {
  return (
    <div className="fl-stack creator-portfolio">
      <header className="fl-pagehead">
        <div>
          <Link className="fl-link" to="/brand/creators">
            ← Back to creator search
          </Link>
          <h1>{p.displayName}</h1>
          <p>Approved creator · Verified portfolio</p>
        </div>
      </header>
      <section className="fl-card fl-stack" aria-label="Creator profile">
        <h2>About the creator</h2>
        <p>{show(p.bio)}</p>
        <div className="creator-portfolio-grid">
          <p>
            <strong>Food category</strong>
            <span>{show(p.foodCategory)}</span>
          </p>
          <p>
            <strong>Location</strong>
            <span>{show(p.location)}</span>
          </p>
          <p>
            <strong>Price range</strong>
            <span>{price(p.priceMinCents, p.priceMaxCents)}</span>
          </p>
        </div>
      </section>
      <section className="fl-card fl-stack" aria-label="Connected social accounts">
        <h2>Social performance</h2>
        {p.socialAccounts.length === 0 ? (
          <p>No connected social accounts yet.</p>
        ) : (
          <div className="creator-portfolio-grid">
            {p.socialAccounts.map((account, i) => (
              <article key={`${account.platform}-${i}`} className="creator-portfolio-item">
                <h3>{account.platform}</h3>
                <p>Followers: {number(account.followers)}</p>
                <p>
                  Engagement:{' '}
                  {account.engagementRate === null ? 'Not available' : `${account.engagementRate}%`}
                </p>
                <p>Last updated: {date(account.lastPulledAt)}</p>
              </article>
            ))}
          </div>
        )}
      </section>
      <section className="fl-card fl-stack" aria-label="Featured campaigns">
        <h2>Featured completed campaigns</h2>
        {p.featuredCampaigns.length === 0 ? (
          <p>No completed featured campaigns yet.</p>
        ) : (
          <div className="creator-portfolio-grid">
            {p.featuredCampaigns.map((c) => (
              <article className="creator-portfolio-item" key={c.campaignId}>
                <h3>{c.name}</h3>
                <p>
                  {c.verified ? (
                    <span className="fl-chip fl-chip--success">Verified result</span>
                  ) : (
                    <span className="fl-chip">Not verified</span>
                  )}
                </p>
                <p>Tracked redemptions: {number(c.redemptions)}</p>
                <p>ROI: {c.roiPercent === null ? 'Not available' : `${c.roiPercent}%`}</p>
              </article>
            ))}
          </div>
        )}
      </section>
      <section className="fl-card fl-stack" aria-label="Ratings and reviews">
        <h2>Ratings and reviews</h2>
        {p.reviewCount === 0 ? (
          <p>No reviews yet.</p>
        ) : (
          <>
            <p>
              <strong>Average rating:</strong>{' '}
              {p.averageRating === null ? 'Not available' : `${p.averageRating} / 5`} (
              {p.reviewCount} reviews)
            </p>
            {p.reviews.map((r, i) => (
              <article className="creator-portfolio-item" key={`${r.createdAt}-${i}`}>
                <strong>{r.rating} / 5</strong>
                <p>{show(r.text)}</p>
                <small>{date(r.createdAt)}</small>
              </article>
            ))}
          </>
        )}
      </section>
    </div>
  )
}

/** EEC-35: read-only, brand-only view of an approved creator's verified portfolio. */
export function CreatorPortfolioPage() {
  const { creatorId } = useParams()
  const valid = creatorId !== undefined && /^[1-9]\d*$/.test(creatorId)
  const result = useFetch<CreatorPortfolio>(
    valid ? `/api/brand/creators/${creatorId}/portfolio` : '/api/brand/creators/invalid/portfolio',
  )
  if (!valid)
    return (
      <div className="fl-card" role="alert">
        Invalid creator ID.
      </div>
    )
  if (result.status === 'loading')
    return (
      <div className="fl-card" role="status">
        Loading creator portfolio…
      </div>
    )
  if (result.status === 'error')
    return (
      <div className="fl-card" role="alert">
        Portfolio unavailable. This creator may not be approved, or the request failed.
      </div>
    )
  return <PortfolioDetails portfolio={result.data} />
}
