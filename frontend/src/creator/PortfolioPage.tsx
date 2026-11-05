import { useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { LOCALE } from '../lib/locale'
import { centsToDollars, parseUSDToCents } from '../lib/moneyInput'
import {
  featureCampaign,
  getPortfolio,
  savePortfolioDetails,
  unfeatureCampaign,
  type CampaignView,
  type Portfolio,
  type SocialAccountView,
} from './portfolioApi'

// The same limits as the server (CreatorProfile). The server is the real gatekeeper; these only stop typing early.
const MAX_BIO_LENGTH = 1000
const MAX_CATEGORY_LENGTH = 50
const MAX_LOCATION_LENGTH = 100

const numberFormat = new Intl.NumberFormat(LOCALE)
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })

// Spacing between blocks comes from the design tokens (docs/README.md, "Styling pages").
const cardSpacing = { marginBottom: 'var(--space-6)' }
const alertSpacing = { marginBottom: 'var(--space-4)' }

type LoadState =
  | { status: 'loading' }
  | { status: 'not-approved'; message: string } // 409: an admin has not approved her profile yet
  | { status: 'error'; message: string }
  | { status: 'ready'; portfolio: Portfolio }

type Message = { kind: 'success' | 'error'; text: string }

/** The creator's Manage Portfolio page (UC-F1.2, EEC-36). */
export function PortfolioPage() {
  const [state, setState] = useState<LoadState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    getPortfolio()
      .then((portfolio) => {
        if (!cancelled) setState({ status: 'ready', portfolio })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 409) {
          setState({ status: 'not-approved', message: error.message })
        } else {
          setState({
            status: 'error',
            message: error instanceof ApiError ? error.message : 'Could not load your portfolio.',
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  function setFeatured(campaignId: number, featured: boolean) {
    // A functional update, so two quick clicks never overwrite each other with an old copy.
    setState((current) =>
      current.status === 'ready'
        ? {
            status: 'ready',
            portfolio: {
              ...current.portfolio,
              completedCampaigns: current.portfolio.completedCampaigns.map((campaign) =>
                campaign.id === campaignId ? { ...campaign, featured } : campaign,
              ),
            },
          }
        : current,
    )
  }

  if (state.status === 'loading') {
    return (
      <main>
        <p>Loading your portfolio…</p>
      </main>
    )
  }
  if (state.status === 'not-approved') {
    return (
      <main>
        <h1>Manage portfolio</h1>
        <div className="fl-alert fl-alert--warning" role="alert">
          <div>
            <b>Your portfolio is not available yet</b>
            {state.message}
          </div>
        </div>
      </main>
    )
  }
  if (state.status === 'error') {
    return (
      <main>
        <h1>Manage portfolio</h1>
        <div className="fl-alert fl-alert--danger" role="alert">
          <div>{state.message}</div>
        </div>
      </main>
    )
  }

  const { portfolio } = state
  return (
    <main>
      <h1>Manage portfolio</h1>
      <p>{portfolio.displayName}</p>

      <section className="fl-card" style={cardSpacing}>
        <h2 className="fl-card__title">Your details</h2>
        <DetailsForm
          portfolio={portfolio}
          onSaved={(saved) => setState({ status: 'ready', portfolio: saved })}
        />
      </section>

      <section className="fl-card" style={cardSpacing}>
        <h2 className="fl-card__title">Connected accounts</h2>
        <Accounts accounts={portfolio.socialAccounts} />
      </section>

      <section className="fl-card" style={cardSpacing}>
        <h2 className="fl-card__title">Completed campaigns</h2>
        <Campaigns campaigns={portfolio.completedCampaigns} onToggled={setFeatured} />
      </section>
    </main>
  )
}

function DetailsForm({
  portfolio,
  onSaved,
}: {
  portfolio: Portfolio
  onSaved: (saved: Portfolio) => void
}) {
  const [bio, setBio] = useState(portfolio.bio ?? '')
  const [foodCategory, setFoodCategory] = useState(portfolio.foodCategory)
  const [location, setLocation] = useState(portfolio.location)
  const [minPrice, setMinPrice] = useState(centsToDollars(portfolio.priceMinCents))
  const [maxPrice, setMaxPrice] = useState(centsToDollars(portfolio.priceMaxCents))
  const [message, setMessage] = useState<Message | null>(null)
  const [saving, setSaving] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage(null)

    const priceMinCents = parseUSDToCents(minPrice)
    const priceMaxCents = parseUSDToCents(maxPrice)
    if (priceMinCents === null || priceMaxCents === null) {
      setMessage({ kind: 'error', text: 'Enter the prices in dollars, like 25 or 25.50.' })
      return
    }

    setSaving(true)
    try {
      const saved = await savePortfolioDetails({
        bio: bio.trim() === '' ? null : bio,
        foodCategory,
        location,
        priceMinCents,
        priceMaxCents,
      })
      onSaved(saved)
      setMessage({ kind: 'success', text: 'Portfolio saved.' })
    } catch (error) {
      // The server's own message, for example when the minimum price is above the maximum.
      setMessage({
        kind: 'error',
        text:
          error instanceof ApiError
            ? error.message
            : 'Could not save your portfolio. Please try again.',
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={save}>
      {message && (
        <div
          className={`fl-alert ${message.kind === 'error' ? 'fl-alert--danger' : 'fl-alert--success'}`}
          role={message.kind === 'error' ? 'alert' : 'status'}
          style={alertSpacing}
        >
          <div>{message.text}</div>
        </div>
      )}
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-bio">
          Bio
        </label>
        <textarea
          id="portfolio-bio"
          className="fl-textarea"
          rows={5}
          maxLength={MAX_BIO_LENGTH}
          value={bio}
          onChange={(event) => setBio(event.target.value)}
        />
        <span className="fl-hint">
          {bio.length}/{MAX_BIO_LENGTH}
        </span>
      </div>
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-category">
          Category
        </label>
        <input
          id="portfolio-category"
          className="fl-input"
          required
          maxLength={MAX_CATEGORY_LENGTH}
          value={foodCategory}
          onChange={(event) => setFoodCategory(event.target.value)}
        />
      </div>
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-location">
          Location
        </label>
        <input
          id="portfolio-location"
          className="fl-input"
          required
          maxLength={MAX_LOCATION_LENGTH}
          value={location}
          onChange={(event) => setLocation(event.target.value)}
        />
      </div>
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-price-min">
          Minimum price (USD)
        </label>
        <div className="fl-affix">
          <span>$</span>
          <input
            id="portfolio-price-min"
            className="fl-input"
            inputMode="decimal"
            required
            value={minPrice}
            onChange={(event) => setMinPrice(event.target.value)}
          />
        </div>
      </div>
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-price-max">
          Maximum price (USD)
        </label>
        <div className="fl-affix">
          <span>$</span>
          <input
            id="portfolio-price-max"
            className="fl-input"
            inputMode="decimal"
            required
            value={maxPrice}
            onChange={(event) => setMaxPrice(event.target.value)}
          />
        </div>
        <span className="fl-hint">Brands see this range on your portfolio.</span>
      </div>
      <button type="submit" className="fl-btn fl-btn--primary" disabled={saving} aria-busy={saving}>
        Save changes
      </button>
    </form>
  )
}

/** Read-only here: connecting and disconnecting an account belongs to UC-F19.4. */
function Accounts({ accounts }: { accounts: SocialAccountView[] }) {
  if (accounts.length === 0) return <p>No social account is connected yet.</p>
  return (
    <div className="fl-tablewrap">
      <table className="fl-table">
        <thead>
          <tr>
            <th>Platform</th>
            <th className="num">Followers</th>
            <th className="num">Engagement</th>
            <th>Last updated</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((account, index) => (
            <tr key={`${account.platform}-${index}`}>
              <td>{account.platform}</td>
              <td className="num">
                {account.followers === null ? '—' : numberFormat.format(account.followers)}
              </td>
              <td className="num">
                {account.engagementRate === null ? '—' : `${account.engagementRate}%`}
              </td>
              <td>
                {account.lastPulledAt === null
                  ? 'Not yet'
                  : dateTimeFormat.format(new Date(account.lastPulledAt))}
              </td>
              <td>
                {account.status === 'NEEDS_RECONNECTION' ? (
                  <span className="fl-chip fl-chip--danger">Needs reconnection</span>
                ) : (
                  <span className="fl-chip fl-chip--success">Connected</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Each checkbox saves straight away: ticking features the campaign, unticking stops featuring it. */
function Campaigns({
  campaigns,
  onToggled,
}: {
  campaigns: CampaignView[]
  onToggled: (campaignId: number, featured: boolean) => void
}) {
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function toggle(campaign: CampaignView) {
    setError(null)
    setBusyId(campaign.id)
    try {
      if (campaign.featured) {
        await unfeatureCampaign(campaign.id)
      } else {
        await featureCampaign(campaign.id)
      }
      onToggled(campaign.id, !campaign.featured)
    } catch (caught) {
      if (
        caught instanceof ApiError &&
        caught.status === 409 &&
        caught.problem.title === 'Already featured'
      ) {
        onToggled(campaign.id, true) // featured meanwhile (another tab): just show the real state
      } else if (caught instanceof ApiError && caught.status === 404 && campaign.featured) {
        onToggled(campaign.id, false) // it was already removed
      } else {
        setError(
          caught instanceof ApiError
            ? caught.message
            : 'Could not update the campaign. Please try again.',
        )
      }
    } finally {
      setBusyId(null)
    }
  }

  if (campaigns.length === 0) {
    return (
      <div className="fl-empty">
        <h3>No completed campaigns yet</h3>
        <p>
          They appear here once a campaign is completed, and you can feature them on your portfolio.
        </p>
      </div>
    )
  }
  return (
    <>
      {error && (
        <div className="fl-alert fl-alert--danger" role="alert" style={alertSpacing}>
          <div>{error}</div>
        </div>
      )}
      <p className="fl-hint">Tick the campaigns you want brands to see on your portfolio.</p>
      <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {campaigns.map((campaign) => (
          <li key={campaign.id} style={{ marginBottom: 'var(--space-3)' }}>
            <label className="fl-check">
              <input
                type="checkbox"
                checked={campaign.featured}
                disabled={busyId !== null}
                onChange={() => void toggle(campaign)}
              />
              <span className="fl-check__box"></span>
              <span>{campaign.name}</span>
            </label>
          </li>
        ))}
      </ul>
    </>
  )
}
