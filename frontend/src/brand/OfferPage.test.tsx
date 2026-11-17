import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { ApiError, apiFetch } from '../api/client'
import type { Offer } from '../api/offers'
import { OfferPage } from './OfferPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

const hamra = { id: 11, name: 'Hamra', location: 'Beirut' }
const achrafieh = { id: 12, name: 'Achrafieh', location: 'Beirut' }
const OFFER = '/api/brand/campaigns/42/offer'

const campaign: Campaign = {
  id: 42,
  name: 'Summer menu launch',
  status: 'AWAITING_CREATOR_RESPONSE',
  creator: { id: 3, displayName: 'Nour Haddad' },
  branches: [hamra],
  priceOfferedCents: 25000,
  startDate: '2030-01-10',
  lastPostingDate: '2030-01-30',
  deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
  contentRequirements: null,
  sentAt: '2030-01-01T10:00:00Z',
  responseDeadline: '2030-01-04T10:00:00Z',
  agreedPriceCents: null,
  paymentDeadline: null,
  acceptedAt: null,
  escrowStatus: null,
  createdAt: '2030-01-01T09:00:00Z',
  updatedAt: '2030-01-01T10:00:00Z',
}

const saved: Offer = {
  id: 5,
  campaignId: 42,
  creatorId: 3,
  discountPercent: 20,
  expiresOn: '2030-02-01',
  expiresAt: '2030-02-01T22:00:00Z',
  validDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  validFrom: '12:00',
  validTo: '15:30',
  allBranches: true,
  branchIds: [],
  minPurchaseCents: 2000,
  maxRedemptions: 100,
  perPersonLimit: 2,
  redemptions: 0,
  state: 'NOT_LIVE_YET',
  editable: true,
  updatedAt: '2030-01-02T10:00:00Z',
}

/** What the app does with each request; a test overrides the ones it cares about. */
function server(
  overrides: Record<string, (init?: { method?: string; body?: unknown }) => unknown> = {},
) {
  apiFetchMock.mockImplementation((path: string, init) => {
    const key = `${(init?.method ?? 'GET').toUpperCase()} ${path}`
    if (overrides[key]) {
      try {
        return Promise.resolve(overrides[key](init))
      } catch (error) {
        return Promise.reject(error)
      }
    }
    switch (key) {
      case 'GET /api/brand/branches':
        return Promise.resolve([hamra, achrafieh])
      case 'GET /api/brand/campaigns/42':
        return Promise.resolve(campaign)
      // No offer yet: the server answers 204, which apiFetch turns into undefined.
      case `GET ${OFFER}`:
        return Promise.resolve(undefined)
      default:
        return Promise.reject(new Error(`no answer for ${key}`))
    }
  })
}

const putBody = () =>
  apiFetchMock.mock.calls.find(([p, init]) => p === OFFER && init?.method === 'PUT')?.[1]?.body

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/brand/campaigns/42/offer']}>
      <Routes>
        <Route path="/brand/campaigns/:id/offer" element={<OfferPage />} />
        <Route path="/brand/campaigns/:id" element={<h1>Campaign page</h1>} />
        <Route path="/brand/campaigns" element={<h1>Campaign list</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

const change = (label: RegExp | string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => {
  apiFetchMock.mockReset()
})

// TC-F10.1.1
it('sets up a 20% offer with conditions and sends every one of them', async () => {
  const user = userEvent.setup()
  server({ [`PUT ${OFFER}`]: () => saved })
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '20')
  change(/Last day it works/, '2030-02-01')
  await user.click(screen.getByRole('checkbox', { name: 'Sat' }))
  await user.click(screen.getByRole('checkbox', { name: 'Sun' }))
  change('From', '12:00')
  change('To', '15:30')
  await user.type(screen.getByLabelText(/Minimum bill/), '20')
  await user.type(screen.getByLabelText(/Maximum uses/), '100')
  const perPerson = screen.getByLabelText(/Uses per person/)
  await user.clear(perPerson)
  await user.type(perPerson, '2')
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  expect(await screen.findByRole('status')).toHaveTextContent(
    'Offer saved. Followers can use it once the campaign is active.',
  )
  expect(putBody()).toEqual({
    discountPercent: 20,
    expiresOn: '2030-02-01',
    validDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    validFrom: '12:00',
    validTo: '15:30',
    allBranches: true,
    branchIds: [],
    minPurchaseCents: 2000,
    maxRedemptions: 100,
    perPersonLimit: 2,
  })
  expect(screen.getByText('Not live yet')).toBeInTheDocument()
  expect(screen.getByText('Used 0 of 100 times.')).toBeInTheDocument()
})

// TC-F10.1.2, EX-F10.1.1
it('starts with every day, all day, all branches and one use per person', async () => {
  const user = userEvent.setup()
  server({ [`PUT ${OFFER}`]: () => ({ ...saved, maxRedemptions: null, perPersonLimit: 1 }) })
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '15')
  expect(screen.getAllByRole('checkbox')).toHaveLength(7)
  for (const checkbox of screen.getAllByRole('checkbox')) expect(checkbox).toBeChecked()
  expect(screen.getByRole('radio', { name: 'All my branches' })).toBeChecked()
  expect(screen.getByLabelText(/Uses per person/)).toHaveValue('1')
  change(/Last day it works/, '2030-02-01')
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  await screen.findByRole('status')
  expect(putBody()).toMatchObject({
    validFrom: null,
    validTo: null,
    allBranches: true,
    minPurchaseCents: null,
    maxRedemptions: null,
    perPersonLimit: 1,
  })
})

// TC-F10.1.3, EX-F10.1.2
it('limits the offer to the branches picked', async () => {
  const user = userEvent.setup()
  server({ [`PUT ${OFFER}`]: () => ({ ...saved, allBranches: false, branchIds: [12] }) })
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '10')
  change(/Last day it works/, '2030-02-01')
  expect(screen.queryByRole('checkbox', { name: /Hamra/ })).not.toBeInTheDocument()
  await user.click(screen.getByRole('radio', { name: 'Only the branches I pick' }))
  await user.click(screen.getByRole('checkbox', { name: /Achrafieh/ }))
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  await screen.findByRole('status')
  expect(putBody()).toMatchObject({ allBranches: false, branchIds: [12] })
})

// TC-F10.1.4, EX-F10.1.3
it('shows what the server refused under each field and keeps what was typed', async () => {
  const user = userEvent.setup()
  server({
    [`PUT ${OFFER}`]: () => {
      throw new ApiError(400, {
        title: 'Invalid offer',
        errors: {
          discountPercent: 'Enter a discount from 1 to 100%.',
          expiresOn: "The expiry date can't be in the past.",
        },
      })
    },
  })
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '150')
  change(/Last day it works/, '2020-01-01')
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  expect(await screen.findByText('Enter a discount from 1 to 100%.')).toBeInTheDocument()
  expect(screen.getByText("The expiry date can't be in the past.")).toBeInTheDocument()
  expect(screen.getByText('Offer not saved. 2 fields need attention.')).toBeInTheDocument()
  expect(screen.getByLabelText(/Discount \(%\)/)).toHaveValue('150')
  expect(screen.getByLabelText(/Discount \(%\)/)).toHaveAttribute('aria-invalid', 'true')
})

it('does not send an amount that is not a number', async () => {
  const user = userEvent.setup()
  server()
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '20')
  await user.type(screen.getByLabelText(/Minimum bill/), 'twenty')
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  expect(await screen.findByText('Enter an amount like 20 or 20.50.')).toBeInTheDocument()
  expect(putBody()).toBeUndefined()
})

it('opens a saved offer with its conditions filled in', async () => {
  server({
    [`GET ${OFFER}`]: () => ({ ...saved, allBranches: false, branchIds: [11] }),
  })
  renderPage()

  expect(await screen.findByLabelText(/Discount \(%\)/)).toHaveValue('20')
  expect(screen.getByLabelText(/Last day it works/)).toHaveValue('2030-02-01')
  expect(screen.getByRole('checkbox', { name: 'Sat' })).not.toBeChecked()
  expect(screen.getByRole('checkbox', { name: 'Mon' })).toBeChecked()
  expect(screen.getByLabelText('From')).toHaveValue('12:00')
  expect(screen.getByRole('radio', { name: 'Only the branches I pick' })).toBeChecked()
  expect(screen.getByRole('checkbox', { name: /Hamra/ })).toBeChecked()
  expect(screen.getByLabelText(/Minimum bill/)).toHaveValue('20.00')
  expect(screen.getByLabelText(/Uses per person/)).toHaveValue('2')
})

it('shows the offer read-only once the campaign is active, with how often it was used', async () => {
  server({
    'GET /api/brand/campaigns/42': () => ({ ...campaign, status: 'ACTIVE' }),
    [`GET ${OFFER}`]: () => ({ ...saved, state: 'LIVE', editable: false, redemptions: 3 }),
  })
  renderPage()

  expect(await screen.findByText('Live')).toBeInTheDocument()
  expect(screen.getByText('Followers can use it now.')).toBeInTheDocument()
  expect(screen.getByText('Used 3 of 100 times.')).toBeInTheDocument()
  expect(screen.getByText('20%')).toBeInTheDocument()
  expect(screen.getByText('Mon, Tue, Wed, Thu, Fri')).toBeInTheDocument()
  expect(screen.getByText('12:00 to 15:30')).toBeInTheDocument()
  expect(screen.getByText('$20.00')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Save offer' })).not.toBeInTheDocument()
  expect(screen.queryByLabelText(/Discount \(%\)/)).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Back to campaign' })).toHaveAttribute(
    'href',
    '/brand/campaigns/42',
  )
})

it('says when an active campaign has no offer, since one cannot be added any more', async () => {
  server({ 'GET /api/brand/campaigns/42': () => ({ ...campaign, status: 'ACTIVE' }) })
  renderPage()

  expect(await screen.findByText(/No follower offer was set up/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Save offer' })).not.toBeInTheDocument()
})

it('locks the form when the campaign went active while the page was open', async () => {
  const user = userEvent.setup()
  let locked = false
  server({
    [`PUT ${OFFER}`]: () => {
      locked = true
      throw new ApiError(409, {
        title: 'Offer locked',
        detail: "The offer can't be set up or changed once the campaign is active or over.",
      })
    },
    [`GET ${OFFER}`]: () => (locked ? { ...saved, state: 'LIVE', editable: false } : undefined),
  })
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '20')
  change(/Last day it works/, '2030-02-01')
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    "The offer can't be set up or changed once the campaign is active or over.",
  )
  await waitFor(() => expect(screen.getByText('Live')).toBeInTheDocument())
  expect(screen.queryByRole('button', { name: 'Save offer' })).not.toBeInTheDocument()
})

it('says so when the campaign cannot be loaded', async () => {
  server({
    'GET /api/brand/campaigns/42': () => {
      throw new ApiError(404, { title: 'Campaign not found' })
    },
  })
  renderPage()

  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the offer.')
  expect(screen.getByRole('link', { name: 'Back to campaigns' })).toBeInTheDocument()
})

// Review of EEC-41: the page keeps showing the previous answer while the next campaign loads.
it("never shows or saves one campaign's offer on another campaign's page", async () => {
  const user = userEvent.setup()
  let release: () => void = () => {}
  const campaign43Ready = new Promise<void>((resolve) => {
    release = resolve
  })
  server({
    [`GET ${OFFER}`]: () => saved,
    'GET /api/brand/campaigns/43': () =>
      campaign43Ready.then(() => ({ ...campaign, id: 43, name: 'Winter menu' })),
    'GET /api/brand/campaigns/43/offer': () =>
      campaign43Ready.then(() => ({ ...saved, id: 6, campaignId: 43, discountPercent: 35 })),
    'PUT /api/brand/campaigns/43/offer': () => ({
      ...saved,
      id: 6,
      campaignId: 43,
      discountPercent: 35,
    }),
  })
  render(
    <MemoryRouter initialEntries={['/brand/campaigns/42/offer']}>
      <Link to="/brand/campaigns/43/offer">Next campaign</Link>
      <Routes>
        <Route path="/brand/campaigns/:id/offer" element={<OfferPage />} />
      </Routes>
    </MemoryRouter>,
  )
  expect(await screen.findByLabelText(/Discount \(%\)/)).toHaveValue('20')

  await user.click(screen.getByRole('link', { name: 'Next campaign' }))

  // While campaign 43 loads, campaign 42's offer is gone: no form at all.
  expect(screen.queryByLabelText(/Discount \(%\)/)).not.toBeInTheDocument()
  expect(screen.getByLabelText('Loading offer')).toBeInTheDocument()

  await act(async () => release())

  expect(await screen.findByLabelText(/Discount \(%\)/)).toHaveValue('35')
  expect(screen.getByText('Winter menu')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Save offer' }))
  await screen.findByRole('status')
  const puts = apiFetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')
  expect(puts.map(([path]) => path)).toEqual(['/api/brand/campaigns/43/offer'])
  expect(puts[0][1]?.body).toMatchObject({ discountPercent: 35 })
})

it('locks the fields while the offer is being saved, so no edit is lost', async () => {
  const user = userEvent.setup()
  let answer: (offer: Offer) => void = () => {}
  server({
    [`PUT ${OFFER}`]: () =>
      new Promise<Offer>((resolve) => {
        answer = resolve
      }),
  })
  renderPage()

  await user.type(await screen.findByLabelText(/Discount \(%\)/), '20')
  change(/Last day it works/, '2030-02-01')
  await user.click(screen.getByRole('button', { name: 'Save offer' }))

  expect(screen.getByLabelText(/Discount \(%\)/)).toBeDisabled()
  expect(screen.getByRole('checkbox', { name: 'Mon' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Saving...' })).toBeDisabled()

  await act(async () => answer(saved))

  expect(await screen.findByRole('status')).toHaveTextContent('Offer saved.')
  expect(screen.getByLabelText(/Discount \(%\)/)).toBeEnabled()
})
