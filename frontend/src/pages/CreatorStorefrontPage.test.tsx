import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { CreatorStorefrontPage } from './CreatorStorefrontPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const fetchMock = vi.mocked(apiFetch)

beforeEach(() => {
  fetchMock.mockReset()
})

function page() {
  return render(
    <MemoryRouter initialEntries={['/s/lara-eats']}>
      <Routes><Route path="/s/:slug" element={<CreatorStorefrontPage />} /></Routes>
    </MemoryRouter>,
  )
}

describe('public creator storefront', () => {
  it('shows live offer and restrictions without requiring an account', async () => {
    fetchMock.mockResolvedValue({
      displayName: 'Lara Eats', bio: 'Food creator', foodCategory: 'Burgers', location: 'Beirut', slug: 'lara-eats',
      offers: [{ id: 1, restaurant: 'Burger House', discountPercent: 20, expiresOn: '2030-02-01',
        validDays: ['MONDAY'], validFrom: '12:00', validTo: '17:00', minPurchaseCents: 2000,
        perPersonLimit: 1, branches: [{ name: 'Downtown', location: 'Beirut' }] }],
    })
    page()
    expect(await screen.findByText('Lara Eats')).toBeInTheDocument()
    expect(screen.getByText('20% off')).toBeInTheDocument()
    expect(screen.getByText('Burger House')).toBeInTheDocument()
    expect(screen.getByText(/Downtown · Beirut/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/public/storefronts/lara-eats')
  })

  it('shows no offers yet for an approved creator without live offers', async () => {
    fetchMock.mockResolvedValue({ displayName: 'Lara', bio: null, foodCategory: 'Food', location: 'Beirut',
      slug: 'lara-eats', offers: [] })
    page()
    expect(await screen.findByText('No offers yet')).toBeInTheDocument()
  })

  it('handles missing or suspended storefronts safely', async () => {
    fetchMock.mockRejectedValue(new Error('Not found'))
    page()
    expect(await screen.findByText('Storefront not available')).toBeInTheDocument()
  })
})
