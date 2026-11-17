import { useCallback, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getCampaign, type Campaign } from '../api/campaigns'
import { ApiError, apiFetch } from '../api/client'
import {
  DAYS,
  OFFER_EDITABLE,
  getOffer,
  offerStateHint,
  offerStateLabel,
  saveOffer,
  type Day,
  type Offer,
  type OfferInput,
  type OfferState,
} from '../api/offers'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { LOCALE } from '../lib/locale'
import { formatUSD } from '../lib/money'
import { centsToDollars, parseUSDToCents } from '../lib/moneyInput'
import { CampaignStatusChip } from './CampaignStatusChip'
import './campaigns.css'

interface Branch {
  id: number
  name: string
  location: string
}

interface Loaded {
  campaign: Campaign
  offer: Offer | null
  branches: Branch[]
}

/** One load, marked with the campaign it was for. */
type Load = { forId: number } & ({ ok: true; data: Loaded } | { ok: false })

/** Everything the brand typed, as text; it becomes numbers and cents only when the request is built. */
interface Values {
  discount: string
  expiresOn: string
  days: Day[]
  from: string
  to: string
  allBranches: boolean
  branchIds: number[]
  minPurchase: string
  maxRedemptions: string
  perPerson: string
}

const dayShort: Record<Day, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
}

const stateTone: Record<OfferState, 'neutral' | 'success' | 'warning' | 'danger' | 'info'> = {
  NOT_LIVE_YET: 'neutral',
  LIVE: 'success',
  PAUSED: 'warning',
  EXPIRED: 'danger',
  FULL: 'info',
  ENDED: 'neutral',
}

const day = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeZone: 'UTC' })
/** A date without a time ("2030-01-10") is shown as that day, whatever the viewer's time zone. */
const dateOnly = (iso: string) => day.format(new Date(`${iso}T00:00:00Z`))

/** Today in the restaurants' time zone, as the date field wants it: the earliest expiry allowed. */
const todayInBeirut = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Beirut' }).format(new Date())

const plural = (n: number) => (n === 1 ? '1 field needs' : `${n} fields need`)

function fromOffer(offer: Offer | null): Values {
  if (offer === null) {
    return {
      discount: '',
      expiresOn: '',
      days: [...DAYS],
      from: '',
      to: '',
      allBranches: true,
      branchIds: [],
      minPurchase: '',
      maxRedemptions: '',
      perPerson: '1',
    }
  }
  return {
    discount: String(offer.discountPercent),
    expiresOn: offer.expiresOn,
    days: offer.validDays,
    from: offer.validFrom ?? '',
    to: offer.validTo ?? '',
    allBranches: offer.allBranches,
    branchIds: offer.branchIds,
    minPurchase: offer.minPurchaseCents === 0 ? '' : centsToDollars(offer.minPurchaseCents),
    maxRedemptions: offer.maxRedemptions === null ? '' : String(offer.maxRedemptions),
    perPerson: String(offer.perPersonLimit),
  }
}

/** A whole number as typed: null when empty, NaN when it is not a whole number. */
function wholeNumber(text: string): number | null {
  const trimmed = text.trim()
  if (trimmed === '') return null
  return /^\d{1,9}$/.test(trimmed) ? Number(trimmed) : Number.NaN
}

/**
 * Turns what was typed into the request. Only what the browser can tell is not a number is caught here; the
 * server checks every rule (UC-F10.1) and names each wrong field.
 */
function toInput(values: Values): { input: OfferInput } | { errors: Record<string, string> } {
  const errors: Record<string, string> = {}
  const discount = wholeNumber(values.discount)
  if (Number.isNaN(discount)) errors.discountPercent = 'Enter a whole number, like 20.'
  const minPurchase = values.minPurchase.trim()
  const minCents = minPurchase === '' ? null : parseUSDToCents(minPurchase)
  if (minPurchase !== '' && minCents === null)
    errors.minPurchaseCents = 'Enter an amount like 20 or 20.50.'
  const max = wholeNumber(values.maxRedemptions)
  if (Number.isNaN(max))
    errors.maxRedemptions = 'Enter a whole number, or leave it empty for no maximum.'
  const perPerson = wholeNumber(values.perPerson)
  if (Number.isNaN(perPerson)) errors.perPersonLimit = 'Enter a whole number, like 1.'
  if (Object.keys(errors).length > 0) return { errors }

  return {
    input: {
      discountPercent: discount,
      expiresOn: values.expiresOn || null,
      validDays: DAYS.filter((d) => values.days.includes(d)),
      validFrom: values.from || null,
      validTo: values.to || null,
      allBranches: values.allBranches,
      branchIds: values.allBranches ? [] : values.branchIds,
      minPurchaseCents: minCents,
      maxRedemptions: max,
      perPersonLimit: perPerson,
    },
  }
}

/**
 * UC-F10.1: the brand sets up the discount the creator's followers get at the restaurant. One offer per campaign;
 * it can be changed until the campaign is active, and is read-only after that (`/brand/campaigns/:id/offer`).
 */
export function OfferPage() {
  const { id } = useParams()
  const campaignId = Number(id)
  // useFetchWith keeps the previous answer while the next one loads. So each answer carries the campaign it was
  // loaded for, and an answer for another campaign counts as still loading: moving from one campaign's offer to
  // another's never opens the form with the previous campaign's conditions, so they can't be saved to this one.
  const load = useCallback(
    (): Promise<Load> =>
      Promise.all([
        getCampaign(campaignId),
        getOffer(campaignId),
        apiFetch<Branch[]>('/api/brand/branches'),
      ]).then(
        ([campaign, offer, branches]): Load => ({
          forId: campaignId,
          ok: true,
          data: { campaign, offer, branches },
        }),
        (): Load => ({ forId: campaignId, ok: false }),
      ),
    [campaignId],
  )
  const result = useFetchWith<Load>(load)

  if (result.status !== 'ready' || result.data.forId !== campaignId) {
    return (
      <div className="fl-card" aria-busy="true" aria-label="Loading offer">
        <div className="fl-skel fl-skel--line" />
      </div>
    )
  }
  const loaded = result.data
  if (!loaded.ok) {
    return (
      <div className="fl-stack">
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load the offer. The campaign may not exist.</div>
        </div>
        <div>
          <Link className="fl-btn fl-btn--outline" to="/brand/campaigns">
            Back to campaigns
          </Link>
        </div>
      </div>
    )
  }
  return <OfferForm key={campaignId} {...loaded.data} />
}

function OfferForm({ campaign, offer: initialOffer, branches }: Loaded) {
  const [offer, setOffer] = useState<Offer | null>(initialOffer)
  const [values, setValues] = useState<Values>(() => fromOffer(initialOffer))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [banner, setBanner] = useState<{ kind: 'error' | 'saved'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  // Set when the server says the campaign went active while this page was open.
  const [locked, setLocked] = useState(false)

  const editable = !locked && (offer ? offer.editable : OFFER_EDITABLE.has(campaign.status))
  const set = (patch: Partial<Values>) => setValues((current) => ({ ...current, ...patch }))
  const toggleDay = (d: Day) =>
    set({
      days: values.days.includes(d) ? values.days.filter((x) => x !== d) : [...values.days, d],
    })
  const toggleBranch = (branchId: number) =>
    set({
      branchIds: values.branchIds.includes(branchId)
        ? values.branchIds.filter((b) => b !== branchId)
        : [...values.branchIds, branchId],
    })

  async function save() {
    setBanner(null)
    setErrors({})
    const built = toInput(values)
    if ('errors' in built) {
      setErrors(built.errors)
      setBanner({
        kind: 'error',
        text: `Offer not saved. ${plural(Object.keys(built.errors).length)} attention.`,
      })
      return
    }
    setBusy(true)
    try {
      const saved = await saveOffer(campaign.id, built.input)
      setOffer(saved)
      setValues(fromOffer(saved))
      setBanner({ kind: 'saved', text: `Offer saved. ${offerStateHint[saved.state]}` })
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.problem.errors) {
        const fields = error.problem.errors
        setErrors(fields)
        setBanner({
          kind: 'error',
          text: `Offer not saved. ${plural(Object.keys(fields).length)} attention.`,
        })
      } else if (error instanceof ApiError && error.status === 409) {
        setLocked(true)
        setBanner({ kind: 'error', text: error.message })
        getOffer(campaign.id)
          .then(setOffer)
          .catch(() => undefined)
      } else {
        setBanner({
          kind: 'error',
          text:
            error instanceof ApiError
              ? error.message
              : 'Offer not saved. Could not reach the server, please try again.',
        })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="fl-stack" onSubmit={(event) => event.preventDefault()} noValidate>
      <header className="fl-pagehead">
        <div>
          <h1>Follower offer</h1>
          <p>
            The discount the creator&apos;s followers get at your restaurant for{' '}
            <strong>{campaign.name}</strong>. It works only while the campaign is active.
          </p>
        </div>
        <CampaignStatusChip status={campaign.status} />
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

      {offer && (
        <section className="fl-card fl-section" aria-label="Offer status">
          <div className="fl-row">
            <span className={`fl-chip fl-chip--${stateTone[offer.state]}`}>
              {offerStateLabel[offer.state]}
            </span>
            <span>{offerStateHint[offer.state]}</span>
          </div>
          <p className="fl-hint">
            Used {offer.redemptions}
            {offer.maxRedemptions === null
              ? offer.redemptions === 1
                ? ' time'
                : ' times'
              : ` of ${offer.maxRedemptions} times`}
            .
          </p>
        </section>
      )}

      {editable ? (
        // Locked while saving: the answer replaces the form's values, so an edit made meanwhile would be lost.
        <fieldset className="fl-fieldset fl-stack" disabled={busy}>
          <OfferFields
            values={values}
            errors={errors}
            branches={branches}
            set={set}
            toggleDay={toggleDay}
            toggleBranch={toggleBranch}
          />
        </fieldset>
      ) : (
        <OfferSummary offer={offer} branches={branches} />
      )}

      <div className="fl-actions">
        <Link className="fl-btn fl-btn--ghost" to={`/brand/campaigns/${campaign.id}`}>
          Back to campaign
        </Link>
        {editable && (
          <button
            className="fl-btn fl-btn--primary"
            type="button"
            disabled={busy}
            onClick={() => void save()}
          >
            {busy ? 'Saving...' : 'Save offer'}
          </button>
        )}
      </div>
    </form>
  )
}

function OfferFields({
  values,
  errors,
  branches,
  set,
  toggleDay,
  toggleBranch,
}: {
  values: Values
  errors: Record<string, string>
  branches: Branch[]
  set: (patch: Partial<Values>) => void
  toggleDay: (d: Day) => void
  toggleBranch: (branchId: number) => void
}) {
  const hoursError = errors.validFrom ?? errors.validTo
  return (
    <>
      <section className="fl-card fl-section" aria-labelledby="offer-discount-title">
        <h2 id="offer-discount-title">Discount</h2>
        <div className="fl-stats">
          <Field id="offer-discount" text="Discount (%)" required error={errors.discountPercent}>
            <div className="fl-affix">
              <input
                className="fl-input"
                id="offer-discount"
                inputMode="numeric"
                placeholder="e.g. 20"
                value={values.discount}
                aria-invalid={Boolean(errors.discountPercent)}
                aria-describedby={errors.discountPercent ? 'offer-discount-error' : undefined}
                onChange={(event) => set({ discount: event.target.value })}
              />
              <span>%</span>
            </div>
          </Field>
          <Field
            id="offer-expiry"
            text="Last day it works"
            required
            error={errors.expiresOn}
            hint="It stops at midnight at the end of this day (Beirut time)."
          >
            <input
              className="fl-input"
              id="offer-expiry"
              type="date"
              min={todayInBeirut()}
              value={values.expiresOn}
              aria-invalid={Boolean(errors.expiresOn)}
              aria-describedby={errors.expiresOn ? 'offer-expiry-error' : 'offer-expiry-hint'}
              onChange={(event) => set({ expiresOn: event.target.value })}
            />
          </Field>
        </div>
      </section>

      <section className="fl-card fl-section" aria-labelledby="offer-when-title">
        <h2 id="offer-when-title">When it can be used</h2>
        <fieldset className={`fl-field fl-fieldset${errors.validDays ? ' is-invalid' : ''}`}>
          <legend className="fl-label">Days</legend>
          <div className="fl-row">
            {DAYS.map((d) => (
              <label className="fl-check" key={d}>
                <input
                  type="checkbox"
                  checked={values.days.includes(d)}
                  onChange={() => toggleDay(d)}
                />
                <span className="fl-check__box"></span>
                <span>{dayShort[d]}</span>
              </label>
            ))}
          </div>
          {errors.validDays && (
            <span className="fl-error" role="alert">
              {errors.validDays}
            </span>
          )}
        </fieldset>

        <fieldset className={`fl-field fl-fieldset${hoursError ? ' is-invalid' : ''}`}>
          <legend className="fl-label">Hours</legend>
          <div className="fl-stats">
            <div className="fl-field">
              <label className="fl-label" htmlFor="offer-from">
                From
              </label>
              <input
                className="fl-input"
                id="offer-from"
                type="time"
                value={values.from}
                aria-invalid={Boolean(errors.validFrom)}
                onChange={(event) => set({ from: event.target.value })}
              />
            </div>
            <div className="fl-field">
              <label className="fl-label" htmlFor="offer-to">
                To
              </label>
              <input
                className="fl-input"
                id="offer-to"
                type="time"
                value={values.to}
                aria-invalid={Boolean(errors.validTo)}
                onChange={(event) => set({ to: event.target.value })}
              />
            </div>
          </div>
          <span className="fl-hint">Leave both empty for all day.</span>
          {hoursError && (
            <span className="fl-error" role="alert">
              {hoursError}
            </span>
          )}
        </fieldset>
      </section>

      <section className="fl-card fl-section" aria-labelledby="offer-where-title">
        <h2 id="offer-where-title">Where it can be used</h2>
        <fieldset className={`fl-field fl-fieldset${errors.branchIds ? ' is-invalid' : ''}`}>
          <legend className="fl-label">Branches</legend>
          <div className="fl-stack">
            <label className="fl-check fl-check--radio">
              <input
                type="radio"
                name="offer-branches"
                checked={values.allBranches}
                onChange={() => set({ allBranches: true })}
              />
              <span className="fl-check__box"></span>
              <span>All my branches</span>
            </label>
            <label className="fl-check fl-check--radio">
              <input
                type="radio"
                name="offer-branches"
                checked={!values.allBranches}
                onChange={() => set({ allBranches: false })}
              />
              <span className="fl-check__box"></span>
              <span>Only the branches I pick</span>
            </label>
            {!values.allBranches &&
              branches.map((branch) => (
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
          {errors.branchIds && (
            <span className="fl-error" role="alert">
              {errors.branchIds}
            </span>
          )}
        </fieldset>
      </section>

      <section className="fl-card fl-section" aria-labelledby="offer-limits-title">
        <h2 id="offer-limits-title">Limits</h2>
        <div className="fl-stats">
          <Field
            id="offer-min"
            text="Minimum bill (USD)"
            error={errors.minPurchaseCents}
            hint="Leave empty for no minimum."
          >
            <div className="fl-affix">
              <span>$</span>
              <input
                className="fl-input"
                id="offer-min"
                inputMode="decimal"
                placeholder="e.g. 20"
                value={values.minPurchase}
                aria-invalid={Boolean(errors.minPurchaseCents)}
                aria-describedby={errors.minPurchaseCents ? 'offer-min-error' : 'offer-min-hint'}
                onChange={(event) => set({ minPurchase: event.target.value })}
              />
            </div>
          </Field>
          <Field
            id="offer-max"
            text="Maximum uses"
            error={errors.maxRedemptions}
            hint="Leave empty for no maximum."
          >
            <input
              className="fl-input"
              id="offer-max"
              inputMode="numeric"
              placeholder="e.g. 100"
              value={values.maxRedemptions}
              aria-invalid={Boolean(errors.maxRedemptions)}
              aria-describedby={errors.maxRedemptions ? 'offer-max-error' : 'offer-max-hint'}
              onChange={(event) => set({ maxRedemptions: event.target.value })}
            />
          </Field>
          <Field
            id="offer-per-person"
            text="Uses per person"
            error={errors.perPersonLimit}
            hint="Once per person if left empty."
          >
            <input
              className="fl-input"
              id="offer-per-person"
              inputMode="numeric"
              value={values.perPerson}
              aria-invalid={Boolean(errors.perPersonLimit)}
              aria-describedby={
                errors.perPersonLimit ? 'offer-per-person-error' : 'offer-per-person-hint'
              }
              onChange={(event) => set({ perPerson: event.target.value })}
            />
          </Field>
        </div>
      </section>
    </>
  )
}

/** The offer as it stands, once the campaign is active or over and it can no longer change. */
function OfferSummary({ offer, branches }: { offer: Offer | null; branches: Branch[] }) {
  if (offer === null) {
    return (
      <div className="fl-alert fl-alert--info" role="status">
        <Icon name="info" />
        <div>
          No follower offer was set up for this campaign. It can&apos;t be added once the campaign
          is active or over.
        </div>
      </div>
    )
  }
  const names = branches.filter((b) => offer.branchIds.includes(b.id)).map((b) => b.name)
  const everyDay = offer.validDays.length === DAYS.length
  return (
    <section className="fl-card fl-section" aria-labelledby="offer-details-title">
      <h2 id="offer-details-title">Conditions</h2>
      <p className="fl-hint">
        The offer can&apos;t be changed now: diners may already hold QR codes for it.
      </p>
      <dl className="fl-kv">
        <dt>Discount</dt>
        <dd>{offer.discountPercent}%</dd>
        <dt>Last day it works</dt>
        <dd>{dateOnly(offer.expiresOn)}</dd>
        <dt>Days</dt>
        <dd>{everyDay ? 'Every day' : offer.validDays.map((d) => dayShort[d]).join(', ')}</dd>
        <dt>Hours</dt>
        <dd>
          {offer.validFrom && offer.validTo ? `${offer.validFrom} to ${offer.validTo}` : 'All day'}
        </dd>
        <dt>Branches</dt>
        <dd>{offer.allBranches ? 'All branches' : names.join(', ')}</dd>
        <dt>Minimum bill</dt>
        <dd>{offer.minPurchaseCents === 0 ? 'None' : formatUSD(offer.minPurchaseCents)}</dd>
        <dt>Maximum uses</dt>
        <dd>{offer.maxRedemptions ?? 'No maximum'}</dd>
        <dt>Uses per person</dt>
        <dd>{offer.perPersonLimit}</dd>
      </dl>
    </section>
  )
}

/** A label, the control, an optional hint and, when the field was refused, the message under it. */
function Field({
  id,
  text,
  required,
  error,
  hint,
  children,
}: {
  id: string
  text: string
  required?: boolean
  error?: string
  hint?: string
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
      {hint && !error && (
        <span className="fl-hint" id={`${id}-hint`}>
          {hint}
        </span>
      )}
      {error && (
        <span className="fl-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
