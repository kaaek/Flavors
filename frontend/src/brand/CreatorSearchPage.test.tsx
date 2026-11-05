import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { CreatorSearchPage } from './CreatorSearchPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

const connected = {
  creatorId: 1,
  displayName: 'Layla Eats',
  foodCategory: 'Burgers',
  location: 'Beirut',
  platform: 'instagram',
  followers: 12000,
  engagementRate: 3.5,
  priceMinCents: 5000,
  priceMaxCents: 20000,
  roi: null,
  redemptions: null,
  storefrontSlug: 'layla-eats',
  saved: false,
}
const noInstagram = {
  ...connected,
  creatorId: 2,
  displayName: 'Omar Bites',
  platform: null,
  followers: null,
  engagementRate: null,
  priceMinCents: null,
  priceMaxCents: null,
  storefrontSlug: null,
}
const page = (items: unknown[]) => ({ items, page: 0, size: 20, totalItems: items.length })

function answers(searchResult: unknown) {
  apiFetchMock.mockImplementation((path: string) =>
    path.startsWith('/api/brand/creators/facets')
      ? Promise.resolve({ locations: ['Beirut'], categories: ['Burgers'] })
      : Promise.resolve(searchResult),
  )
}
const lastSearch = () =>
  apiFetchMock.mock.calls.map(([path]) => path).filter((path) => path.includes('/search'))

function renderPage() {
  render(
    <MemoryRouter>
      <CreatorSearchPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('lists creators, shows a dash for figures she has no data for, and links the storefront', async () => {
  answers(page([connected, noInstagram]))
  renderPage()

  expect(await screen.findByRole('link', { name: 'Layla Eats' })).toHaveAttribute(
    'href',
    '/s/layla-eats',
  )
  expect(screen.getByText('12,000')).toBeInTheDocument()
  expect(screen.getByText('$50.00 – $200.00')).toBeInTheDocument()
  expect(screen.getByText('Omar Bites')).toBeInTheDocument()
  // Omar: platform, followers, engagement, price, plus ROI and redemptions for both creators.
  expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(8)
  expect(screen.getAllByRole('button', { name: 'Create campaign' })[0]).toBeDisabled()
  expect(screen.getAllByRole('button', { name: 'Save' })).toHaveLength(2)
})

it('sends the plain search text after typing, with the first page', async () => {
  const user = userEvent.setup()
  answers(page([connected]))
  renderPage()
  await screen.findByText('Layla Eats')

  await user.type(screen.getByLabelText('Search creators'), 'burgers beirut')

  await vi.waitFor(() =>
    expect(lastSearch()).toContain('/api/brand/creators/search?q=burgers+beirut&page=0&size=20'),
  )
})

it('turns advanced filters into the query: dollars become cents', async () => {
  const user = userEvent.setup()
  answers(page([connected]))
  renderPage()
  await screen.findByText('Layla Eats')

  expect(screen.queryByLabelText('Min followers')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Advanced' }))
  await user.type(screen.getByLabelText('Location'), 'Beirut')
  await user.type(screen.getByLabelText('Category'), 'Burgers')
  await user.type(screen.getByLabelText('Min followers'), '5000')
  await user.type(screen.getByLabelText('Min price $'), '25.50')
  await user.type(screen.getByLabelText('Max price $'), '100')

  await vi.waitFor(() =>
    expect(lastSearch()).toContain(
      '/api/brand/creators/search?location=Beirut&category=Burgers&minFollowers=5000&minPriceCents=2550&maxPriceCents=10000&page=0&size=20',
    ),
  )
  expect(screen.getByText('Min followers: 5000')).toBeInTheDocument()
})

it('explains an empty result and Clear all filters starts over', async () => {
  const user = userEvent.setup()
  answers(page([]))
  renderPage()

  await user.type(screen.getByLabelText('Search creators'), 'zzz')
  expect(await screen.findByText('No creators found')).toBeInTheDocument()
  expect(screen.getByText(/loosening a filter/)).toBeInTheDocument()

  await user.click(screen.getAllByRole('button', { name: 'Clear all filters' })[0])
  expect(screen.getByLabelText('Search creators')).toHaveValue('')
  await vi.waitFor(() =>
    expect(lastSearch().at(-1)).toBe('/api/brand/creators/search?page=0&size=20'),
  )
})

it('pages through results', async () => {
  const user = userEvent.setup()
  answers({ items: [connected], page: 0, size: 20, totalItems: 45 })
  renderPage()
  await screen.findByText('Layla Eats')

  await user.click(screen.getByRole('button', { name: 'Next' }))
  await vi.waitFor(() => expect(lastSearch().at(-1)).toContain('page=1'))
})

it('shows an error when the search fails', async () => {
  apiFetchMock.mockImplementation((path: string) =>
    path.includes('/search') ? Promise.reject(new ApiError(500, {})) : Promise.resolve({}),
  )
  renderPage()

  expect(await screen.findByText('Could not load creators. Please try again.')).toBeInTheDocument()
})
