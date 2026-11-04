import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { Icon } from '../layout/Icon'
import { centsToDollars, parseUSDToCents } from '../lib/moneyInput'
import { AccountCard } from './AccountCard'
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
      <div className="fl-card" aria-busy="true">
        Loading your portfolio…
      </div>
    )
  }
  if (state.status === 'not-approved' || state.status === 'error') {
    const notApproved = state.status === 'not-approved'
    return (
      <div className="fl-stack">
        <header className="fl-pagehead">
          <div>
            <h1>Manage portfolio</h1>
          </div>
        </header>
        <div className={`fl-alert fl-alert--${notApproved ? 'warning' : 'danger'}`} role="alert">
          <Icon name={notApproved ? 'clock' : 'alert'} />
          <div>
            {state.message}
            {notApproved && (
              <>
                {' '}
                <Link className="fl-link" to="/creator/status">
                  See your profile status
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
    )
  }

  const { portfolio } = state
  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Manage portfolio</h1>
          <p>{portfolio.displayName}</p>
        </div>
      </header>

      <section className="fl-card fl-stack" aria-labelledby="details-title">
        <h2 className="fl-card__title" id="details-title">
          Your details
        </h2>
        <DetailsForm
          portfolio={portfolio}
          onSaved={(saved) => setState({ status: 'ready', portfolio: saved })}
        />
      </section>

      <section className="fl-stack" aria-labelledby="accounts-title">
        <div className="fl-row fl-row--between">
          <h2 className="fl-section-title" id="accounts-title">
            Connected accounts
          </h2>
          <Link className="fl-btn fl-btn--outline fl-btn--sm" to="/creator/social">
            {portfolio.socialAccounts.length === 0 ? 'Connect Instagram' : 'Manage connections'}
          </Link>
        </div>
        <Accounts accounts={portfolio.socialAccounts} />
      </section>

      <section className="fl-stack" aria-labelledby="campaigns-title">
        <h2 className="fl-section-title" id="campaigns-title">
          Completed campaigns
        </h2>
        <Campaigns campaigns={portfolio.completedCampaigns} onToggled={setFeatured} />
      </section>
    </div>
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
    <form className="fl-stack" onSubmit={save}>
      {message && (
        <div
          className={`fl-alert fl-alert--${message.kind === 'error' ? 'danger' : 'success'}`}
          role={message.kind === 'error' ? 'alert' : 'status'}
        >
          <Icon name={message.kind === 'error' ? 'alert' : 'check-circle'} />
          <div>{message.text}</div>
        </div>
      )}
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-bio">
          Bio
        </label>
        <textarea
          className="fl-textarea"
          id="portfolio-bio"
          name="bio"
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
          className="fl-input"
          id="portfolio-category"
          name="foodCategory"
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
          className="fl-input"
          id="portfolio-location"
          name="location"
          required
          maxLength={MAX_LOCATION_LENGTH}
          value={location}
          onChange={(event) => setLocation(event.target.value)}
        />
      </div>
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-min">
          Minimum price (USD)
        </label>
        <div className="fl-affix">
          <span>$</span>
          <input
            className="fl-input"
            id="portfolio-min"
            name="priceMin"
            inputMode="decimal"
            required
            value={minPrice}
            onChange={(event) => setMinPrice(event.target.value)}
          />
        </div>
      </div>
      <div className="fl-field">
        <label className="fl-label" htmlFor="portfolio-max">
          Maximum price (USD)
        </label>
        <div className="fl-affix">
          <span>$</span>
          <input
            className="fl-input"
            id="portfolio-max"
            name="priceMax"
            inputMode="decimal"
            required
            value={maxPrice}
            onChange={(event) => setMaxPrice(event.target.value)}
          />
        </div>
      </div>
      <div className="fl-row">
        <button className="fl-btn fl-btn--primary" type="submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </form>
  )
}

/** Read-only here: connecting and disconnecting an account is on the Social accounts page (UC-F19.4). */
function Accounts({ accounts }: { accounts: SocialAccountView[] }) {
  if (accounts.length === 0) {
    return (
      <div className="fl-card fl-empty">
        <img
          className="fl-empty__art-img"
          src="/illustrations/spots/empty-creators.svg"
          alt=""
          aria-hidden="true"
        />
        <h3>No social account is connected yet.</h3>
        <p>Brands trust numbers that come straight from Instagram.</p>
      </div>
    )
  }
  return (
    <div className="fl-stats fl-stats--wide">
      {accounts.map((account, index) => (
        <AccountCard key={`${account.platform}-${index}`} account={account} />
      ))}
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
      <div className="fl-card fl-empty">
        <img
          className="fl-empty__art-img"
          src="/illustrations/spots/empty-campaigns.svg"
          alt=""
          aria-hidden="true"
        />
        <h3>No completed campaigns yet</h3>
        <p>You have no completed campaigns yet. They appear here once a campaign is completed.</p>
      </div>
    )
  }
  return (
    <>
      {error && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>{error}</div>
        </div>
      )}
      <div className="fl-card fl-stack">
        <p className="fl-muted">Tick the campaigns you want brands to see on your portfolio.</p>
        {campaigns.map((campaign) => (
          <label className="fl-check" key={campaign.id}>
            <input
              type="checkbox"
              checked={campaign.featured}
              disabled={busyId !== null}
              onChange={() => void toggle(campaign)}
            />
            <span className="fl-check__box"></span>
            <span>{campaign.name}</span>
          </label>
        ))}
      </div>
    </>
  )
}
