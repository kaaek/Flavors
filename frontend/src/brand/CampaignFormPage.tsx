import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  DELIVERABLE_TYPES,
  PLATFORMS,
  createDraft,
  getCampaign,
  getCampaignCreator,
  sendDraft,
  sendNew,
  updateDraft,
  type Campaign,
  type CampaignInput,
} from '../api/campaigns'
import { ApiError, apiFetch } from '../api/client'
import { searchCreators, type CreatorSearchItem } from '../api/creatorSearch'
import { Icon } from '../layout/Icon'
import { centsToDollars, parseUSDToCents } from '../lib/moneyInput'
import { useDebounced } from '../lib/useDebounced'
import './campaigns.css'

interface Branch {
  id: number
  name: string
  location: string
}

interface CreatorChoice {
  id: number
  displayName: string
  detail: string
}

/** One deliverable row of the form; `key` only keeps React's list stable while rows are added and removed. */
interface Row {
  key: number
  platform: string
  type: string
  count: string
}

/** Everything the brand typed, as text; it becomes cents, dates and numbers only when the request is built. */
interface Values {
  name: string
  creator: CreatorChoice | null
  branchIds: number[]
  price: string
  startDate: string
  lastPostingDate: string
  rows: Row[]
  content: string
}

const MAX_NAME = 150
const MAX_CONTENT = 2000
const label = (text: string) => text.charAt(0) + text.slice(1).toLowerCase()

let nextKey = 1
const newRow = (patch: Partial<Row> = {}): Row => ({
  key: nextKey++,
  platform: PLATFORMS[0],
  type: DELIVERABLE_TYPES[0],
  count: '1',
  ...patch,
})

function fromCampaign(campaign: Campaign): Values {
  return {
    name: campaign.name,
    creator: campaign.creator && {
      id: campaign.creator.id,
      displayName: campaign.creator.displayName,
      detail: '',
    },
    branchIds: campaign.branches.map((b) => b.id),
    price: campaign.priceOfferedCents === null ? '' : centsToDollars(campaign.priceOfferedCents),
    startDate: campaign.startDate ?? '',
    lastPostingDate: campaign.lastPostingDate ?? '',
    rows: campaign.deliverables.map((d) => newRow({ ...d, count: String(d.count) })),
    content: campaign.contentRequirements ?? '',
  }
}

/** Turns what was typed into the request. Returns a message instead when the price is not an amount. */
function toInput(values: Values): { input: CampaignInput } | { priceError: string } {
  const price = values.price.trim()
  const cents = price === '' ? null : parseUSDToCents(price)
  if (price !== '' && cents === null) return { priceError: 'Enter an amount like 250 or 250.50.' }
  return {
    input: {
      name: values.name,
      creatorId: values.creator?.id ?? null,
      branchIds: values.branchIds,
      priceOfferedCents: cents,
      startDate: values.startDate || null,
      lastPostingDate: values.lastPostingDate || null,
      deliverables: values.rows.map((row) => {
        const count = Number(row.count)
        return {
          platform: row.platform,
          type: row.type,
          count: row.count.trim() !== '' && Number.isInteger(count) ? count : null,
        }
      }),
      contentRequirements: values.content.trim() === '' ? null : values.content,
    },
  }
}

const emptyValues = (): Values => ({
  name: '',
  creator: null,
  branchIds: [],
  price: '',
  startDate: '',
  lastPostingDate: '',
  rows: [newRow()],
  content: '',
})

const plural = (n: number) => (n === 1 ? '1 field needs' : `${n} fields need`)

/**
 * UC-F3.1: the brand's form to create a campaign, save it as a draft and send it to one creator. It is the same
 * page for a new campaign (`/brand/campaigns/new`, optionally `?creator=<id>` from a creator search result) and for
 * reopening a draft (`/brand/campaigns/:id/edit`).
 */
export function CampaignFormPage() {
  const { id } = useParams()
  const [search] = useSearchParams()
  const campaignId = id === undefined ? null : Number(id)
  const creatorParam = Number(search.get('creator'))

  const [branches, setBranches] = useState<Branch[] | null>(null)
  // What was loaded, and for which campaign/creator: opening another one must not show the previous form's values.
  const [loaded, setLoaded] = useState<{ for: string; values: Values } | null>(null)
  const loadKey = campaignId === null ? `new:${creatorParam}` : `edit:${campaignId}`
  const [empty] = useState(emptyValues)
  const chosenCreator = Number.isInteger(creatorParam) && creatorParam > 0
  // A plain "New campaign" has nothing to load.
  const initial =
    campaignId === null && !chosenCreator ? empty : loaded?.for === loadKey ? loaded.values : null
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
    let cancelled = false
    apiFetch<Branch[]>('/api/brand/branches')
      .then((list) => !cancelled && setBranches(list))
      .catch(() => !cancelled && setLoadError(true))
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const setInitial = (values: Values) => setLoaded({ for: loadKey, values })
    if (campaignId === null && !chosenCreator) return
    if (campaignId !== null) {
      getCampaign(campaignId)
        .then((campaign) => !cancelled && setInitial(fromCampaign(campaign)))
        .catch(() => !cancelled && setLoadError(true))
    } else {
      // "Start a campaign" on a search result: open the form with that creator already chosen. If she can't be
      // found (e.g. no longer approved) the form still opens, and the brand picks someone else.
      getCampaignCreator(creatorParam)
        .then(
          (c) =>
            !cancelled &&
            setInitial({
              ...empty,
              creator: {
                id: c.id,
                displayName: c.displayName,
                detail: `${c.foodCategory} · ${c.location}`,
              },
            }),
        )
        .catch(() => !cancelled && setInitial(empty))
    }
    return () => {
      cancelled = true
    }
  }, [campaignId, creatorParam, chosenCreator, loadKey, empty])

  if (loadError) {
    return (
      <div className="fl-alert fl-alert--danger" role="alert">
        <Icon name="alert" />
        <div>Could not load this campaign. Please try again.</div>
      </div>
    )
  }
  if (branches === null || initial === null) {
    return (
      <div className="fl-card" aria-busy="true" aria-label="Loading campaign">
        <div className="fl-skel fl-skel--line" />
      </div>
    )
  }
  // EX-F3.1.3: no branch, no campaign.
  if (branches.length === 0) return <NoBranch />

  return (
    // The key restarts the form when another campaign is opened, so nothing typed leaks across.
    <CampaignForm key={loadKey} campaignId={campaignId} initial={initial} branches={branches} />
  )
}

function NoBranch() {
  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <h1>New campaign</h1>
      </header>
      <div className="fl-alert fl-alert--warning" role="alert">
        <Icon name="alert" />
        <div>You need at least one branch before you can create a campaign.</div>
      </div>
      <div>
        <Link className="fl-btn fl-btn--primary" to="/brand/account">
          Add a branch
        </Link>
      </div>
    </div>
  )
}

function CampaignForm({
  campaignId: initialId,
  initial,
  branches,
}: {
  campaignId: number | null
  initial: Values
  branches: Branch[]
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const campaignId = initialId
  const [values, setValues] = useState<Values>(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [banner, setBanner] = useState<{ kind: 'error' | 'saved'; text: string } | null>(
    (location.state as { saved?: boolean } | null)?.saved
      ? { kind: 'saved', text: DRAFT_SAVED }
      : null,
  )
  const [busy, setBusy] = useState(false)
  const [blocked, setBlocked] = useState(false)

  const set = (patch: Partial<Values>) => setValues((current) => ({ ...current, ...patch }))
  const setRow = (key: number, patch: Partial<Row>) =>
    setValues((current) => ({
      ...current,
      rows: current.rows.map((row) => (row.key === key ? { ...row, ...patch } : row)),
    }))

  async function submit(action: 'draft' | 'send') {
    setBanner(null)
    setErrors({})
    const built = toInput(values)
    if ('priceError' in built) {
      setErrors({ priceOfferedCents: built.priceError })
      setBanner({
        kind: 'error',
        text: `${action === 'send' ? 'Not sent' : 'Draft not saved'}. 1 field needs attention.`,
      })
      return
    }
    setBusy(true)
    try {
      if (action === 'draft') {
        const saved =
          campaignId === null
            ? await createDraft(built.input)
            : await updateDraft(campaignId, built.input)
        if (campaignId === null) {
          // Reopen it as a saved draft, so the address is the one to come back to.
          navigate(`/brand/campaigns/${saved.id}/edit`, { replace: true, state: { saved: true } })
        } else {
          setBanner({ kind: 'saved', text: DRAFT_SAVED })
        }
      } else {
        const sent =
          campaignId === null
            ? await sendNew(built.input)
            : await sendDraft(campaignId, built.input)
        navigate(`/brand/campaigns/${sent.id}`, { state: { sent: true } })
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.problem.errors) {
        const fields = error.problem.errors
        setErrors(fields)
        const count = Object.keys(fields).length
        setBanner({
          kind: 'error',
          text:
            action === 'send'
              ? `Not sent. ${plural(count)} attention. You can complete them or save a draft.`
              : `Draft not saved. ${plural(count)} attention.`,
        })
      } else if (
        error instanceof ApiError &&
        error.status === 409 &&
        error.problem.title === 'No branch'
      ) {
        setBlocked(true)
      } else {
        setBanner({
          kind: 'error',
          text:
            error instanceof ApiError
              ? error.message
              : `${action === 'send' ? 'Not sent' : 'Draft not saved'}. Could not reach the server, please try again.`,
        })
      }
    } finally {
      setBusy(false)
    }
  }

  if (blocked) return <NoBranch />

  const toggleBranch = (branchId: number) =>
    set({
      branchIds: values.branchIds.includes(branchId)
        ? values.branchIds.filter((b) => b !== branchId)
        : [...values.branchIds, branchId],
    })

  // Enter in a text field must not send: sending is a deliberate click on the button.
  return (
    <form className="fl-stack" onSubmit={(event) => event.preventDefault()} noValidate>
      <header className="fl-pagehead">
        <div>
          <h1>{campaignId === null ? 'New campaign' : 'Edit campaign'}</h1>
          <p>
            Explain what you want from the creator and what you offer. Nothing reaches her until you
            send.
          </p>
        </div>
      </header>

      {banner && (
        <div
          className={`fl-alert ${banner.kind === 'error' ? 'fl-alert--danger' : 'fl-alert--success'}`}
          role={banner.kind === 'error' ? 'alert' : 'status'}
        >
          <Icon name={banner.kind === 'error' ? 'alert' : 'check-circle'} />
          <div>{banner.text}</div>
        </div>
      )}

      <section className="fl-card fl-section" aria-labelledby="basics-title">
        <h2 id="basics-title">Basics</h2>
        <Field id="campaign-name" text="Campaign name" required error={errors.name}>
          <input
            className="fl-input"
            id="campaign-name"
            maxLength={MAX_NAME}
            value={values.name}
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? 'campaign-name-error' : undefined}
            onChange={(event) => set({ name: event.target.value })}
          />
        </Field>

        <fieldset className={`fl-field fl-fieldset${errors.branchIds ? ' is-invalid' : ''}`}>
          <legend className="fl-label">
            Branches{' '}
            <span className="fl-required" aria-hidden="true">
              *
            </span>
          </legend>
          <div className="fl-stack">
            {branches.map((branch) => (
              <label className="fl-check" key={branch.id}>
                <input
                  type="checkbox"
                  checked={values.branchIds.includes(branch.id)}
                  onChange={() => toggleBranch(branch.id)}
                />
                <span className="fl-check__box"></span>
                <span>
                  {branch.name} <span className="fl-hint">· {branch.location}</span>
                </span>
              </label>
            ))}
          </div>
          <span className="fl-hint">Pick one or more of your branches.</span>
          {errors.branchIds && (
            <span className="fl-error" id="campaign-branches-error" role="alert">
              {errors.branchIds}
            </span>
          )}
        </fieldset>

        <CreatorPicker
          value={values.creator}
          error={errors.creatorId}
          onChange={(creator) => set({ creator })}
        />
      </section>

      <section className="fl-card fl-section" aria-labelledby="offer-title">
        <h2 id="offer-title">Offer and dates</h2>
        <div className="fl-stats">
          <Field
            id="campaign-price"
            text="Price offered (USD)"
            required
            error={errors.priceOfferedCents}
          >
            <div className="fl-affix">
              <span>$</span>
              <input
                className="fl-input"
                id="campaign-price"
                inputMode="decimal"
                placeholder="e.g. 250"
                value={values.price}
                aria-invalid={Boolean(errors.priceOfferedCents)}
                aria-describedby={errors.priceOfferedCents ? 'campaign-price-error' : undefined}
                onChange={(event) => set({ price: event.target.value })}
              />
            </div>
          </Field>
        </div>
        <div className="fl-stats">
          <Field id="campaign-start" text="Start date" required error={errors.startDate}>
            <input
              className="fl-input"
              id="campaign-start"
              type="date"
              value={values.startDate}
              aria-invalid={Boolean(errors.startDate)}
              aria-describedby={errors.startDate ? 'campaign-start-error' : undefined}
              onChange={(event) => set({ startDate: event.target.value })}
            />
          </Field>
          <Field
            id="campaign-last"
            text="Last posting date"
            required
            error={errors.lastPostingDate}
          >
            <input
              className="fl-input"
              id="campaign-last"
              type="date"
              value={values.lastPostingDate}
              aria-invalid={Boolean(errors.lastPostingDate)}
              aria-describedby={errors.lastPostingDate ? 'campaign-last-error' : undefined}
              onChange={(event) => set({ lastPostingDate: event.target.value })}
            />
          </Field>
        </div>
      </section>

      <section className="fl-card fl-section" aria-labelledby="deliverables-title">
        <h2 id="deliverables-title">
          Deliverables{' '}
          <span className="fl-required" aria-hidden="true">
            *
          </span>
        </h2>
        {values.rows.map((row, index) => (
          <div
            className="fl-deliverable"
            key={row.key}
            role="group"
            aria-label={`Deliverable ${index + 1}`}
          >
            <Field
              id={`deliverable-${row.key}-platform`}
              text="Platform"
              error={errors[`deliverables[${index}].platform`]}
            >
              <select
                className="fl-select"
                id={`deliverable-${row.key}-platform`}
                value={row.platform}
                onChange={(event) => setRow(row.key, { platform: event.target.value })}
              >
                {PLATFORMS.map((platform) => (
                  <option key={platform} value={platform}>
                    {label(platform)}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              id={`deliverable-${row.key}-type`}
              text="Type"
              error={errors[`deliverables[${index}].type`]}
            >
              <select
                className="fl-select"
                id={`deliverable-${row.key}-type`}
                value={row.type}
                onChange={(event) => setRow(row.key, { type: event.target.value })}
              >
                {DELIVERABLE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {label(type)}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              id={`deliverable-${row.key}-count`}
              text="Count"
              error={errors[`deliverables[${index}].count`]}
            >
              <input
                className="fl-input"
                id={`deliverable-${row.key}-count`}
                inputMode="numeric"
                value={row.count}
                aria-invalid={Boolean(errors[`deliverables[${index}].count`])}
                aria-describedby={
                  errors[`deliverables[${index}].count`]
                    ? `deliverable-${row.key}-count-error`
                    : undefined
                }
                onChange={(event) => setRow(row.key, { count: event.target.value })}
              />
            </Field>
            <button
              className="fl-btn fl-btn--ghost fl-deliverable__remove"
              type="button"
              aria-label={`Remove deliverable ${index + 1}`}
              onClick={() => set({ rows: values.rows.filter((r) => r.key !== row.key) })}
            >
              <Icon name="x" />
            </button>
          </div>
        ))}
        {errors.deliverables && (
          <span className="fl-error" role="alert">
            {errors.deliverables}
          </span>
        )}
        <div>
          <button
            className="fl-btn fl-btn--outline"
            type="button"
            onClick={() => set({ rows: [...values.rows, newRow()] })}
          >
            <Icon name="plus" />
            Add a deliverable
          </button>
        </div>
      </section>

      <section className="fl-card fl-section" aria-labelledby="content-title">
        <h2 id="content-title">Content requirements</h2>
        <Field
          id="campaign-content"
          text="What should the creator know?"
          error={errors.contentRequirements}
        >
          <textarea
            className="fl-textarea"
            id="campaign-content"
            maxLength={MAX_CONTENT}
            placeholder="Tone, dishes to show, hashtags, things to avoid."
            value={values.content}
            onChange={(event) => set({ content: event.target.value })}
          />
        </Field>
      </section>

      <div className="fl-actions">
        <Link className="fl-btn fl-btn--ghost" to="/brand/campaigns">
          Cancel
        </Link>
        <button
          className="fl-btn fl-btn--outline"
          type="button"
          disabled={busy}
          onClick={() => void submit('draft')}
        >
          Save draft
        </button>
        <button
          className="fl-btn fl-btn--primary"
          type="button"
          disabled={busy}
          onClick={() => void submit('send')}
        >
          Send to creator
        </button>
      </div>
    </form>
  )
}

const DRAFT_SAVED = 'Draft saved. Only you can see it; the creator has not been told.'

/** A label, the control and, when the server refused the field, the message under it. */
function Field({
  id,
  text,
  required,
  error,
  children,
}: {
  id: string
  text: string
  required?: boolean
  error?: string
  children: ReactNode
}) {
  return (
    <div className={`fl-field${error ? ' is-invalid' : ''}`}>
      <label className="fl-label" htmlFor={id}>
        {text}
        {required && (
          <span className="fl-required" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      {children}
      {error && (
        <span className="fl-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      )}
    </div>
  )
}

/** The chosen creator, or a small search to choose one (exactly one per campaign, FR-F3.1). */
function CreatorPicker({
  value,
  error,
  onChange,
}: {
  value: CreatorChoice | null
  error?: string
  onChange: (creator: CreatorChoice) => void
}) {
  const [picking, setPicking] = useState(value === null)
  const [q, setQ] = useState('')
  const debounced = useDebounced(q)
  // The answer remembers which search it belongs to, so a slow older answer never shows under a newer search.
  const [answer, setAnswer] = useState<{ q: string; items: CreatorSearchItem[] | null } | null>(
    null,
  )
  const term = debounced.trim()

  useEffect(() => {
    if (!picking) return
    let cancelled = false
    searchCreators({ q: term, size: 5 })
      .then((page) => !cancelled && setAnswer({ q: term, items: page.items }))
      .catch(() => !cancelled && setAnswer({ q: term, items: null }))
    return () => {
      cancelled = true
    }
  }, [picking, term])

  const current = answer?.q === term ? answer : null
  const results = current?.items ?? null
  const failed = current !== null && current.items === null

  return (
    <div className={`fl-field${error ? ' is-invalid' : ''}`}>
      <span className="fl-label" id="campaign-creator-label">
        Creator{' '}
        <span className="fl-required" aria-hidden="true">
          *
        </span>
      </span>
      {value && (
        <div className="fl-creator" aria-labelledby="campaign-creator-label">
          <div className="fl-creator__meta">
            <b>{value.displayName}</b>
            {value.detail && <span>{value.detail}</span>}
          </div>
          {!picking && (
            <button
              className="fl-btn fl-btn--ghost fl-btn--sm"
              type="button"
              onClick={() => setPicking(true)}
            >
              Change
            </button>
          )}
          {picking && (
            <button
              className="fl-btn fl-btn--ghost fl-btn--sm"
              type="button"
              onClick={() => setPicking(false)}
            >
              Keep {value.displayName}
            </button>
          )}
        </div>
      )}
      {picking && (
        <div className="fl-picker">
          <label className="fl-label" htmlFor="campaign-creator-search">
            Search creators
          </label>
          <input
            className="fl-input"
            id="campaign-creator-search"
            type="search"
            placeholder="Search by food, city or name"
            value={q}
            onChange={(event) => setQ(event.target.value)}
          />
          {failed && <span className="fl-error">Could not load creators. Please try again.</span>}
          {results && results.length === 0 && <span className="fl-hint">No creators found.</span>}
          {results && results.length > 0 && (
            <ul className="fl-picker__list">
              {results.map((creator) => (
                <li key={creator.creatorId}>
                  <span>
                    <b>{creator.displayName}</b>
                    <span className="fl-hint">
                      {' '}
                      · {[creator.foodCategory, creator.location].filter(Boolean).join(' · ')}
                    </span>
                  </span>
                  <button
                    className="fl-btn fl-btn--outline fl-btn--sm"
                    type="button"
                    aria-label={`Choose ${creator.displayName}`}
                    onClick={() => {
                      onChange({
                        id: creator.creatorId,
                        displayName: creator.displayName,
                        detail: [creator.foodCategory, creator.location]
                          .filter(Boolean)
                          .join(' · '),
                      })
                      setPicking(false)
                    }}
                  >
                    Choose
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {error && (
        <span className="fl-error" id="campaign-creator-error" role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
