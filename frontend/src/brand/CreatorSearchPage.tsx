import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getCreatorFacets,
  searchCreators,
  type CreatorFacets,
  type CreatorSearchItem,
  type CreatorSearchParams,
} from '../api/creatorSearch'
import { Icon } from '../layout/Icon'
import { Pager } from '../layout/Pager'
import { LOCALE } from '../lib/locale'
import { formatUSD } from '../lib/money'
import { parseUSDToCents } from '../lib/moneyInput'
import { useDebounced } from '../lib/useDebounced'
import { useSearchResults } from '../lib/useSearchResults'
import { SaveCreatorButton } from './SaveCreatorButton'

const PAGE_SIZE = 20

/** What the brand typed. Everything is text here; it becomes numbers/cents only when the request is built. */
interface Form {
  q: string
  location: string
  category: string
  platform: string
  minFollowers: string
  maxFollowers: string
  minEngagement: string
  minPrice: string
  maxPrice: string
  minRoi: string
  minRedemptions: string
  page: number
}

const emptyForm: Form = {
  q: '',
  location: '',
  category: '',
  platform: '',
  minFollowers: '',
  maxFollowers: '',
  minEngagement: '',
  minPrice: '',
  maxPrice: '',
  minRoi: '',
  minRedemptions: '',
  page: 0,
}

/** Labels for the active-filter chips; the keys are the advanced fields (not q or page). */
const filterLabels: Record<Exclude<keyof Form, 'q' | 'page'>, string> = {
  location: 'Location',
  category: 'Category',
  platform: 'Platform',
  minFollowers: 'Min followers',
  maxFollowers: 'Max followers',
  minEngagement: 'Min engagement %',
  minPrice: 'Min price $',
  maxPrice: 'Max price $',
  minRoi: 'Min ROI',
  minRedemptions: 'Min redemptions',
}
type FilterKey = keyof typeof filterLabels

const num = (text: string) => {
  const value = Number(text)
  return text.trim() !== '' && Number.isFinite(value) ? value : undefined
}

/** A price field is valid when empty or a dollar amount; anything else is ignored in the request and flagged in the form. */
const priceError = (text: string) =>
  text.trim() !== '' && parseUSDToCents(text) === null ? 'Enter an amount like 25 or 25.50' : ''

function toParams(form: Form): CreatorSearchParams {
  return {
    q: form.q.trim(),
    location: form.location.trim(),
    category: form.category.trim(),
    platform: form.platform,
    minFollowers: num(form.minFollowers),
    maxFollowers: num(form.maxFollowers),
    minEngagement: num(form.minEngagement),
    // Dollars typed by the brand go over the wire as integer cents (no floating-point money).
    minPriceCents: parseUSDToCents(form.minPrice) ?? undefined,
    maxPriceCents: parseUSDToCents(form.maxPrice) ?? undefined,
    minRoi: num(form.minRoi),
    minRedemptions: num(form.minRedemptions),
    page: form.page,
    size: PAGE_SIZE,
  }
}

const dash = '—'
const count = (n: number | null) => (n === null ? dash : n.toLocaleString(LOCALE))

function priceRange(item: CreatorSearchItem) {
  const { priceMinCents: min, priceMaxCents: max } = item
  if (min === null && max === null) return dash
  if (min === null || max === null) return formatUSD((min ?? max) as number)
  return `${formatUSD(min)} – ${formatUSD(max)}`
}

/** UC-F2.1: a brand finds approved creators with one plain search bar, or narrows them with the advanced filters. */
export function CreatorSearchPage() {
  const [form, setForm] = useState<Form>(emptyForm)
  const [advanced, setAdvanced] = useState(false)
  const [facets, setFacets] = useState<CreatorFacets>({ locations: [], categories: [] })

  // Suggestions are a nicety: if they fail to load, the fields still work as free text.
  useEffect(() => {
    getCreatorFacets()
      .then(setFacets)
      .catch(() => undefined)
  }, [])

  // Wait for a pause in typing before asking the server; the inputs themselves stay instant.
  const debounced = useDebounced(form)
  const params = useMemo(() => toParams(debounced), [debounced])
  const results = useSearchResults(params, searchCreators)

  // Any change to what is searched for goes back to the first page; only the pager changes `page` alone.
  function change(patch: Partial<Form>) {
    setForm((current) => ({ ...current, ...patch, page: 0 }))
  }

  const activeFilters = (Object.keys(filterLabels) as FilterKey[]).filter(
    (key) => form[key].trim() !== '',
  )
  const hasCriteria = form.q.trim() !== '' || activeFilters.length > 0

  function field(key: FilterKey, type: 'text' | 'number' = 'text', extra?: { list?: string }) {
    const error = key === 'minPrice' || key === 'maxPrice' ? priceError(form[key]) : ''
    return (
      <div className={`fl-field${error ? ' is-invalid' : ''}`} key={key}>
        <label className="fl-label" htmlFor={`creator-filter-${key}`}>
          {filterLabels[key]}
        </label>
        <input
          className="fl-input"
          id={`creator-filter-${key}`}
          type={type}
          min={type === 'number' ? 0 : undefined}
          inputMode={type === 'number' ? 'decimal' : undefined}
          list={extra?.list}
          value={form[key]}
          aria-invalid={Boolean(error)}
          onChange={(event) => change({ [key]: event.target.value })}
        />
        {error && (
          <span className="fl-error" role="alert">
            {error}
          </span>
        )}
      </div>
    )
  }

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Find creators</h1>
          <p>Search verified food creators by food, city or name.</p>
        </div>
      </header>

      <div className="fl-card fl-stack">
        <div className="fl-field">
          <label className="fl-label" htmlFor="creator-search-q">
            Search creators
          </label>
          <input
            className="fl-input"
            id="creator-search-q"
            type="search"
            placeholder="Search by food, city or name… e.g. burgers beirut"
            value={form.q}
            onChange={(event) => change({ q: event.target.value })}
          />
        </div>

        <div className="fl-row">
          <button
            className="fl-btn fl-btn--outline fl-btn--sm"
            type="button"
            aria-expanded={advanced}
            aria-controls="creator-advanced"
            onClick={() => setAdvanced((open) => !open)}
          >
            Advanced
          </button>
          {activeFilters.map((key) => (
            <span className="fl-chip fl-chip--info" key={key}>
              {filterLabels[key]}: {form[key]}
              <button
                className="fl-iconbtn"
                type="button"
                aria-label={`Remove ${filterLabels[key]} filter`}
                onClick={() => change({ [key]: '' })}
              >
                <Icon name="x" />
              </button>
            </span>
          ))}
          {hasCriteria && (
            <button
              className="fl-btn fl-btn--ghost fl-btn--sm"
              type="button"
              onClick={() => setForm(emptyForm)}
            >
              Clear all filters
            </button>
          )}
        </div>

        {advanced && (
          <div id="creator-advanced" className="fl-stack">
            <datalist id="creator-locations">
              {facets.locations.map((value) => (
                <option key={value} value={value} />
              ))}
            </datalist>
            <datalist id="creator-categories">
              {facets.categories.map((value) => (
                <option key={value} value={value} />
              ))}
            </datalist>
            <div className="fl-stats">
              {field('location', 'text', { list: 'creator-locations' })}
              {field('category', 'text', { list: 'creator-categories' })}
              <div className="fl-field">
                <label className="fl-label" htmlFor="creator-filter-platform">
                  {filterLabels.platform}
                </label>
                <select
                  className="fl-select"
                  id="creator-filter-platform"
                  value={form.platform}
                  onChange={(event) => change({ platform: event.target.value })}
                >
                  <option value="">Any</option>
                  <option value="instagram">Instagram</option>
                </select>
              </div>
              {field('minFollowers', 'number')}
              {field('maxFollowers', 'number')}
              {field('minEngagement', 'number')}
              {field('minPrice')}
              {field('maxPrice')}
              {field('minRoi', 'number')}
              {field('minRedemptions', 'number')}
            </div>
            <p className="fl-hint">
              Followers, engagement, ROI and redemptions only match creators who have that data, so
              setting them may hide creators who have not connected Instagram yet.
            </p>
          </div>
        )}
      </div>

      <p className="fl-hint">
        Campaigns are not built yet, so “Create campaign” is switched off for now.
      </p>

      {results.status === 'error' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load creators. Please try again.</div>
        </div>
      )}

      {results.status === 'loading' && (
        <div className="fl-card" aria-busy="true" aria-label="Loading creators">
          <div className="fl-skel fl-skel--line" />
        </div>
      )}

      {results.status === 'ready' && results.data.items.length === 0 && (
        <div className="fl-card fl-empty">
          <h2>No creators found</h2>
          <p>Try loosening a filter or checking the spelling of your search.</p>
          {hasCriteria && (
            <button
              className="fl-btn fl-btn--outline"
              type="button"
              onClick={() => setForm(emptyForm)}
            >
              Clear all filters
            </button>
          )}
        </div>
      )}

      {results.status === 'ready' && results.data.items.length > 0 && (
        <>
          <div className="fl-tablewrap">
            <table className="fl-table">
              <thead>
                <tr>
                  <th scope="col">Creator</th>
                  <th scope="col">Food</th>
                  <th scope="col">Location</th>
                  <th scope="col">Platform</th>
                  <th scope="col">Followers</th>
                  <th scope="col">Engagement</th>
                  <th scope="col">Price range</th>
                  <th scope="col">ROI</th>
                  <th scope="col">Redemptions</th>
                  <th scope="col">
                    <span className="fl-sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {results.data.items.map((item) => (
                  <tr key={item.creatorId}>
                    <td>
                      {item.storefrontSlug ? (
                        <Link className="fl-link" to={`/s/${item.storefrontSlug}`}>
                          {item.displayName}
                        </Link>
                      ) : (
                        item.displayName
                      )}
                    </td>
                    <td>{item.foodCategory ?? dash}</td>
                    <td>{item.location ?? dash}</td>
                    <td>{item.platform ?? dash}</td>
                    <td>{count(item.followers)}</td>
                    <td>{item.engagementRate === null ? dash : `${item.engagementRate}%`}</td>
                    <td>{priceRange(item)}</td>
                    <td>{item.roi === null ? dash : item.roi}</td>
                    <td>{count(item.redemptions)}</td>
                    <td>
                      <div className="fl-row">
                        <SaveCreatorButton creatorId={item.creatorId} initiallySaved={item.saved} />
                        {/* A live link would be a dead end until the campaign story exists (and fail the link audit). */}
                        <button
                          className="fl-btn fl-btn--primary fl-btn--sm"
                          type="button"
                          disabled
                        >
                          Create campaign
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pager
              page={results.data.page}
              size={results.data.size}
              total={results.data.totalItems}
              onPage={(page) => setForm((current) => ({ ...current, page }))}
            />
          </div>
        </>
      )}
    </div>
  )
}
