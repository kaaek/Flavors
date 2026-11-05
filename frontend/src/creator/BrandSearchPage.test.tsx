import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { BrandSearchPage } from './BrandSearchPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

const brand = {
  brandId: 4,
  businessName: 'Burger Joint',
  cuisine: 'Burgers',
  description: 'Smash burgers since 2015',
  branchLocations: ['Beirut', 'Jounieh'],
}
const page = (items: unknown[]) => ({ items, page: 0, size: 20, totalItems: items.length })
const searches = () => apiFetchMock.mock.calls.map(([path]) => path)

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('shows brand cards without any contact details', async () => {
  apiFetchMock.mockResolvedValue(page([brand]))
  render(<BrandSearchPage />)

  expect(await screen.findByText('Burger Joint')).toBeInTheDocument()
  expect(screen.getByText('Burgers')).toBeInTheDocument()
  expect(screen.getByText('Smash burgers since 2015')).toBeInTheDocument()
  expect(screen.getByText('Branches: Beirut, Jounieh')).toBeInTheDocument()
})

it('sends the search text and the advanced cuisine and location filters', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValue(page([brand]))
  render(<BrandSearchPage />)
  await screen.findByText('Burger Joint')

  await user.type(screen.getByLabelText('Search brands'), 'burgr')
  await vi.waitFor(() =>
    expect(searches()).toContain('/api/creator/brands/search?q=burgr&page=0&size=20'),
  )

  await user.click(screen.getByRole('button', { name: 'Advanced' }))
  await user.type(screen.getByLabelText('Cuisine'), 'Burgers')
  await user.type(screen.getByLabelText('Location'), 'Beirut')
  await vi.waitFor(() =>
    expect(searches()).toContain(
      '/api/creator/brands/search?q=burgr&cuisine=Burgers&location=Beirut&page=0&size=20',
    ),
  )
})

it('explains an empty result and Clear all filters starts over', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValue(page([]))
  render(<BrandSearchPage />)

  await user.type(screen.getByLabelText('Search brands'), 'zzz')
  expect(await screen.findByText('No brands found')).toBeInTheDocument()
  await user.click(screen.getAllByRole('button', { name: 'Clear all filters' })[0])
  expect(screen.getByLabelText('Search brands')).toHaveValue('')
})

it('shows an error when the search fails', async () => {
  apiFetchMock.mockRejectedValue(new ApiError(500, {}))
  render(<BrandSearchPage />)

  expect(await screen.findByText('Could not load brands. Please try again.')).toBeInTheDocument()
})
