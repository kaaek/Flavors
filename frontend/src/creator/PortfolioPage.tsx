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
  if (state.status === 'not-approved' || state.status === 'error') {
    return (
      <main>
        <h1>Manage portfolio</h1>
        <p role="alert">{state.message}</p>
      </main>
    )
  }

  const { portfolio } = state
  return (
    <main>
      <h1>Manage portfolio</h1>
      <p>{portfolio.displayName}</p>

      <h2>Your details</h2>
      <DetailsForm
        portfolio={portfolio}
        onSaved={(saved) => setState({ status: 'ready', portfolio: saved })}
      />

      <h2>Connected accounts</h2>
      <Accounts accounts={portfolio.socialAccounts} />

      <h2>Completed campaigns</h2>
      <Campaigns campaigns={portfolio.completedCampaigns} onToggled={setFeatured} />
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
      {message && <p role={message.kind === 'error' ? 'alert' : 'status'}>{message.text}</p>}
      <label>
        Bio
        <textarea
          name="bio"
          rows={5}
          maxLength={MAX_BIO_LENGTH}
          value={bio}
          onChange={(event) => setBio(event.target.value)}
        />
      </label>
      <p>
        {bio.length}/{MAX_BIO_LENGTH}
      </p>
      <label>
        Category
        <input
          name="foodCategory"
          required
          maxLength={MAX_CATEGORY_LENGTH}
          value={foodCategory}
          onChange={(event) => setFoodCategory(event.target.value)}
        />
      </label>
      <label>
        Location
        <input
          name="location"
          required
          maxLength={MAX_LOCATION_LENGTH}
          value={location}
          onChange={(event) => setLocation(event.target.value)}
        />
      </label>
      <label>
        Minimum price (USD)
        <input
          name="priceMin"
          inputMode="decimal"
          required
          value={minPrice}
          onChange={(event) => setMinPrice(event.target.value)}
        />
      </label>
      <label>
        Maximum price (USD)
        <input
          name="priceMax"
          inputMode="decimal"
          required
          value={maxPrice}
          onChange={(event) => setMaxPrice(event.target.value)}
        />
      </label>
      <button type="submit" disabled={saving}>
        {saving ? 'Saving…' : 'Save changes'}
      </button>
    </form>
  )
}

/** Read-only here: connecting and disconnecting an account belongs to UC-F19.4. */
function Accounts({ accounts }: { accounts: SocialAccountView[] }) {
  if (accounts.length === 0) return <p>No social account is connected yet.</p>
  return (
    <ul>
      {accounts.map((account, index) => (
        <li key={`${account.platform}-${index}`}>
          <strong>{account.platform}</strong>:{' '}
          {account.followers === null ? '—' : numberFormat.format(account.followers)} followers
          {' · '}
          {account.engagementRate === null ? '—' : `${account.engagementRate}%`} engagement
          {' · '}
          Last updated{' '}
          {account.lastPulledAt === null
            ? 'not yet'
            : dateTimeFormat.format(new Date(account.lastPulledAt))}
          {account.status === 'NEEDS_RECONNECTION' && (
            <>
              {' · '}
              <strong>Needs reconnection</strong>
            </>
          )}
        </li>
      ))}
    </ul>
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
      <p>You have no completed campaigns yet. They appear here once a campaign is completed.</p>
    )
  }
  return (
    <>
      {error && <p role="alert">{error}</p>}
      <ul>
        {campaigns.map((campaign) => (
          <li key={campaign.id}>
            <label>
              <input
                type="checkbox"
                checked={campaign.featured}
                disabled={busyId !== null}
                onChange={() => void toggle(campaign)}
              />
              {campaign.name}
            </label>
          </li>
        ))}
      </ul>
    </>
  )
}
