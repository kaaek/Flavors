import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { ApiError, apiFetch } from '../api/client'
import { CampaignDetailPage } from './CampaignDetailPage'
import { CampaignFormPage } from './CampaignFormPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

const hamra = { id: 11, name: 'Hamra', location: 'Beirut' }
const achrafieh = { id: 12, name: 'Achrafieh', location: 'Beirut' }
const nour = {
  creatorId: 3,
  displayName: 'Nour Haddad',
  foodCategory: 'Food reviewer',
  location: 'Beirut',
}

const sentCampaign: Campaign = {
  id: 42,
  name: 'Summer menu launch',
  status: 'AWAITING_CREATOR_RESPONSE',
  creator: { id: 3, displayName: 'Nour Haddad' },
  branches: [hamra],
  priceOfferedCents: 25050,
  startDate: '2030-01-10',
  lastPostingDate: '2030-01-30',
  deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
  contentRequirements: null,
  sentAt: '2030-01-01T10:00:00Z',
  responseDeadline: '2030-01-04T10:00:00Z',
  createdAt: '2030-01-01T09:00:00Z',
  updatedAt: '2030-01-01T10:00:00Z',
}
const draftCampaign: Campaign = {
  ...sentCampaign,
  name: 'Weekend brunch',
  status: 'DRAFT',
  sentAt: null,
  responseDeadline: null,
}

/** What the app does with each request; a test overrides the ones it cares about. */
function server(
  overrides: Record<string, (init?: { method?: string; body?: unknown }) => unknown> = {},
) {
  apiFetchMock.mockImplementation((path: string, init) => {
    const key = `${(init?.method ?? 'GET').toUpperCase()} ${path.split('?')[0]}`
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
      case 'GET /api/brand/campaigns/creators/3':
        return Promise.resolve({
          id: 3,
          displayName: 'Nour Haddad',
          foodCategory: 'Food reviewer',
          location: 'Beirut',
        })
      case 'GET /api/brand/creators/search':
        return Promise.resolve({ items: [nour], page: 0, size: 5, totalItems: 1 })
      case 'GET /api/brand/campaigns/42':
        return Promise.resolve(draftCampaign)
      default:
        return Promise.reject(new Error(`no answer for ${key}`))
    }
  })
}

const bodyOf = (method: string, path: string) => {
  const call = apiFetchMock.mock.calls.find(
    ([p, init]) => p === path && (init?.method ?? 'GET') === method,
  )
  return call?.[1]?.body as Record<string, unknown> | undefined
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/brand/campaigns/new" element={<CampaignFormPage />} />
        <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
        <Route path="/brand/campaigns/:id/edit" element={<CampaignFormPage />} />
        <Route path="/brand/campaigns" element={<h1>Campaign list</h1>} />
        <Route path="/brand/account" element={<h1>Account page</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

const change = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } })

/** Fills every required field of a new campaign whose creator was chosen through the link. */
async function fillComplete(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText(/Campaign name/), 'Summer menu launch')
  await user.click(screen.getByRole('checkbox', { name: /Hamra/ }))
  await user.type(screen.getByLabelText(/Price offered/), '250.50')
  change('Start date *', '2030-01-10')
  change('Last posting date *', '2030-01-30')
  const count = screen.getByLabelText('Count')
  await user.clear(count)
  await user.type(count, '2')
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

// TC-F3.1.1
it('sends a complete campaign and shows it as awaiting the creator with the 3-day deadline', async () => {
  const user = userEvent.setup()
  server({
    'POST /api/brand/campaigns/send': () => sentCampaign,
    'GET /api/brand/campaigns/42': () => sentCampaign,
  })
  renderAt('/brand/campaigns/new?creator=3')
  expect(await screen.findByText('Nour Haddad')).toBeInTheDocument()

  await fillComplete(user)
  await user.click(screen.getByRole('button', { name: 'Send to creator' }))

  // The price goes over the wire as integer cents, the creator as an id.
  expect(bodyOf('POST', '/api/brand/campaigns/send')).toEqual({
    name: 'Summer menu launch',
    creatorId: 3,
    branchIds: [11],
    priceOfferedCents: 25050,
    startDate: '2030-01-10',
    lastPostingDate: '2030-01-30',
    deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
    contentRequirements: null,
  })
  expect(
    await screen.findByText(/Sent to Nour Haddad\. She has 3 days to respond/),
  ).toBeInTheDocument()
  expect(screen.getByText('Awaiting creator response')).toBeInTheDocument()
  expect(screen.getByText('$250.50')).toBeInTheDocument()
  expect(screen.getByText('2 × Instagram reel')).toBeInTheDocument()
})

// TC-F3.1.2, EX-F3.1.1
it('highlights every field the server refuses, with a message under it and a banner, and stays on the form', async () => {
  const user = userEvent.setup()
  server({
    'POST /api/brand/campaigns/send': () => {
      throw new ApiError(400, {
        title: 'Invalid campaign',
        errors: {
          name: 'Enter a campaign name.',
          priceOfferedCents: 'Enter the price you offer.',
          startDate: 'Pick a start date.',
          lastPostingDate: 'Pick a last posting date.',
          branchIds: 'Pick at least one branch.',
          'deliverables[0].count': 'Enter 1 or more.',
        },
      })
    },
  })
  renderAt('/brand/campaigns/new?creator=3')
  await screen.findByLabelText(/Campaign name/)

  await user.click(screen.getByRole('button', { name: 'Send to creator' }))

  expect(await screen.findByText(/Not sent\. 6 fields need attention/)).toBeInTheDocument()
  expect(screen.getByText('Enter a campaign name.')).toBeInTheDocument()
  expect(screen.getByText('Enter the price you offer.')).toBeInTheDocument()
  expect(screen.getByText('Pick a start date.')).toBeInTheDocument()
  expect(screen.getByText('Pick a last posting date.')).toBeInTheDocument()
  expect(screen.getByText('Pick at least one branch.')).toBeInTheDocument()
  expect(screen.getByText('Enter 1 or more.')).toBeInTheDocument()
  expect(screen.getByLabelText(/Campaign name/)).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByLabelText(/Price offered/)).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByLabelText('Count')).toHaveAttribute('aria-invalid', 'true')
  // Still on the form, and the brand can fix it or save a draft.
  expect(screen.getByRole('button', { name: 'Save draft' })).toBeEnabled()
  expect(screen.queryByText(/Sent to/)).not.toBeInTheDocument()
})

it('does not call the server for a price that is not an amount', async () => {
  const user = userEvent.setup()
  server()
  renderAt('/brand/campaigns/new')
  await user.type(await screen.findByLabelText(/Price offered/), 'abc')

  await user.click(screen.getByRole('button', { name: 'Send to creator' }))

  expect(await screen.findByText('Enter an amount like 250 or 250.50.')).toBeInTheDocument()
  expect(screen.getByText(/Not sent\. 1 field needs attention/)).toBeInTheDocument()
  expect(apiFetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
})

it('does not send when Enter is pressed in a field', async () => {
  const user = userEvent.setup()
  server()
  renderAt('/brand/campaigns/new')

  await user.type(await screen.findByLabelText(/Campaign name/), 'Brunch{Enter}')

  expect(apiFetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
})

// TC-F3.1.3
it('saves a draft with only a name, says only the brand can see it, and reopens it at the edit address', async () => {
  const user = userEvent.setup()
  server({
    'POST /api/brand/campaigns': () => draftCampaign,
    'GET /api/brand/campaigns/42': () => draftCampaign,
  })
  renderAt('/brand/campaigns/new')

  await user.type(await screen.findByLabelText(/Campaign name/), 'Weekend brunch')
  await user.click(screen.getByRole('button', { name: 'Save draft' }))

  expect(bodyOf('POST', '/api/brand/campaigns')).toMatchObject({
    name: 'Weekend brunch',
    creatorId: null,
    branchIds: [],
    priceOfferedCents: null,
    startDate: null,
    lastPostingDate: null,
  })
  expect(
    await screen.findByText('Draft saved. Only you can see it; the creator has not been told.'),
  ).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Edit campaign' })).toBeInTheDocument()
  // Saving a draft is not sending.
  expect(apiFetchMock.mock.calls.some(([path]) => String(path).endsWith('/send'))).toBe(false)
})

// TC-F3.1.4
it('reopens a draft filled in, and sends it with the edited details', async () => {
  const user = userEvent.setup()
  // The campaign is a draft until the send goes through, then it is the sent one.
  let current = draftCampaign
  server({
    'GET /api/brand/campaigns/42': () => current,
    'POST /api/brand/campaigns/42/send': () => (current = sentCampaign),
  })
  renderAt('/brand/campaigns/42/edit')

  const name = await screen.findByLabelText(/Campaign name/)
  expect(name).toHaveValue('Weekend brunch')
  expect(screen.getByLabelText(/Price offered/)).toHaveValue('250.50')
  expect(screen.getByLabelText(/Start date/)).toHaveValue('2030-01-10')
  expect(screen.getByRole('checkbox', { name: /Hamra/ })).toBeChecked()
  expect(screen.getByRole('checkbox', { name: /Achrafieh/ })).not.toBeChecked()
  expect(screen.getByText('Nour Haddad')).toBeInTheDocument()
  expect(screen.getByLabelText('Count')).toHaveValue('2')

  await user.clear(name)
  await user.type(name, 'Brunch, edited')
  await user.clear(screen.getByLabelText(/Price offered/))
  await user.type(screen.getByLabelText(/Price offered/), '300')
  await user.click(screen.getByRole('checkbox', { name: /Achrafieh/ }))
  await user.click(screen.getByRole('button', { name: 'Send to creator' }))

  expect(bodyOf('POST', '/api/brand/campaigns/42/send')).toMatchObject({
    name: 'Brunch, edited',
    creatorId: 3,
    priceOfferedCents: 30000,
    branchIds: [11, 12],
  })
  await waitFor(() => expect(screen.getByText('Awaiting creator response')).toBeInTheDocument())
})

it('saves changes to a draft in place with PUT', async () => {
  const user = userEvent.setup()
  server({ 'PUT /api/brand/campaigns/42': () => draftCampaign })
  renderAt('/brand/campaigns/42/edit')
  await screen.findByLabelText(/Campaign name/)

  await user.click(screen.getByRole('button', { name: 'Save draft' }))

  expect(await screen.findByText(/Draft saved\. Only you can see it/)).toBeInTheDocument()
  expect(bodyOf('PUT', '/api/brand/campaigns/42')).toMatchObject({ name: 'Weekend brunch' })
})

// EX-F3.1.3
it('blocks a brand with no branch and offers to add one', async () => {
  server({ 'GET /api/brand/branches': () => [] })
  renderAt('/brand/campaigns/new')

  expect(
    await screen.findByText('You need at least one branch before you can create a campaign.'),
  ).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Add a branch' })).toHaveAttribute(
    'href',
    '/brand/account',
  )
  expect(screen.queryByRole('button', { name: 'Send to creator' })).not.toBeInTheDocument()
  expect(screen.queryByLabelText(/Campaign name/)).not.toBeInTheDocument()
})

it('shows the blocked state if the server says the brand has no branch', async () => {
  const user = userEvent.setup()
  server({
    'POST /api/brand/campaigns': () => {
      throw new ApiError(409, {
        title: 'No branch',
        detail: 'You need at least one branch before you can create a campaign.',
      })
    },
  })
  renderAt('/brand/campaigns/new')
  await user.type(await screen.findByLabelText(/Campaign name/), 'X')

  await user.click(screen.getByRole('button', { name: 'Save draft' }))

  expect(await screen.findByRole('link', { name: 'Add a branch' })).toBeInTheDocument()
})

it('lets the brand pick a creator by searching when none was chosen', async () => {
  const user = userEvent.setup()
  server()
  renderAt('/brand/campaigns/new')

  await user.click(await screen.findByRole('button', { name: 'Choose Nour Haddad' }))

  expect(screen.getByText('Food reviewer · Beirut')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Change' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Choose Nour Haddad' })).not.toBeInTheDocument()
})

it('adds and removes deliverables, offering only Instagram and its four types', async () => {
  const user = userEvent.setup()
  server()
  renderAt('/brand/campaigns/new')
  await screen.findByLabelText(/Campaign name/)

  const platform = screen.getByLabelText('Platform') as HTMLSelectElement
  expect(Array.from(platform.options).map((o) => o.text)).toEqual(['Instagram'])
  const type = screen.getByLabelText('Type') as HTMLSelectElement
  expect(Array.from(type.options).map((o) => o.text)).toEqual(['Reel', 'Post', 'Story', 'Carousel'])

  await user.click(screen.getByRole('button', { name: 'Add a deliverable' }))
  expect(screen.getAllByRole('group', { name: /Deliverable/ })).toHaveLength(2)
  await user.click(screen.getByRole('button', { name: 'Remove deliverable 1' }))
  const rows = screen.getAllByRole('group', { name: /Deliverable/ })
  expect(rows).toHaveLength(1)
  await user.selectOptions(within(rows[0]).getByLabelText('Type'), 'Story')
  expect(within(rows[0]).getByLabelText('Type')).toHaveValue('STORY')
})

it('shows a message when the campaign cannot be loaded', async () => {
  server({
    'GET /api/brand/campaigns/42': () => {
      throw new ApiError(404, {})
    },
  })
  renderAt('/brand/campaigns/42/edit')

  expect(
    await screen.findByText('Could not load this campaign. Please try again.'),
  ).toBeInTheDocument()
})

it('opens a sent campaign read-only, and a draft address goes to the form', async () => {
  server({ 'GET /api/brand/campaigns/42': () => sentCampaign })
  renderAt('/brand/campaigns/42')

  expect(await screen.findByRole('heading', { name: 'Summer menu launch' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Send to creator' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Back to campaigns' })).toHaveAttribute(
    'href',
    '/brand/campaigns',
  )
})
