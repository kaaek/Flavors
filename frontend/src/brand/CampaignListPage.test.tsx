import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { CampaignListPage } from './CampaignListPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

function renderPage() {
  render(
    <MemoryRouter>
      <CampaignListPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('lists name, creator and status, with Edit on drafts only, and a New campaign button', async () => {
  apiFetchMock.mockResolvedValue([
    {
      id: 1,
      name: 'Summer menu launch',
      status: 'AWAITING_CREATOR_RESPONSE',
      creatorId: 3,
      creatorName: 'Nour Haddad',
      priceOfferedCents: 25000,
      updatedAt: '2030-01-01T10:00:00Z',
    },
    {
      id: 2,
      name: 'Weekend brunch',
      status: 'DRAFT',
      creatorId: null,
      creatorName: null,
      priceOfferedCents: null,
      updatedAt: '2030-01-01T09:00:00Z',
    },
  ])
  renderPage()

  const sent = (await screen.findByRole('link', { name: 'Summer menu launch' })).closest('tr')!
  expect(within(sent).getByText('Nour Haddad')).toBeInTheDocument()
  expect(within(sent).getByText('Awaiting creator response')).toBeInTheDocument()
  expect(within(sent).getByText('$250.00')).toBeInTheDocument()
  expect(within(sent).queryByRole('link', { name: /Edit/ })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Summer menu launch' })).toHaveAttribute(
    'href',
    '/brand/campaigns/1',
  )

  const draft = screen.getByRole('link', { name: 'Weekend brunch' }).closest('tr')!
  expect(within(draft).getByText('Not chosen yet')).toBeInTheDocument()
  expect(within(draft).getByText('Draft')).toBeInTheDocument()
  expect(within(draft).getByRole('link', { name: 'Edit Weekend brunch' })).toHaveAttribute(
    'href',
    '/brand/campaigns/2/edit',
  )
  expect(screen.getByRole('link', { name: /New campaign/ })).toHaveAttribute(
    'href',
    '/brand/campaigns/new',
  )
})

it('shows an empty state with a way to start', async () => {
  apiFetchMock.mockResolvedValue([])
  renderPage()

  expect(await screen.findByRole('heading', { name: 'No campaigns yet' })).toBeInTheDocument()
  expect(screen.getAllByRole('link', { name: /New campaign/ }).length).toBeGreaterThan(0)
})

it('says so when the list cannot be loaded', async () => {
  apiFetchMock.mockRejectedValue(new Error('down'))
  renderPage()

  expect(
    await screen.findByText('Could not load your campaigns. Please try again.'),
  ).toBeInTheDocument()
})
