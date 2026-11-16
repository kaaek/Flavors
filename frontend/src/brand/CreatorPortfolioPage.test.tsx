import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { CreatorPortfolioPage } from './CreatorPortfolioPage'
import type { CreatorPortfolio } from '../api/creatorPortfolio'

vi.mock('../api/client', async (original) => ({
  ...(await original<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const fetchMock = vi.mocked(apiFetch)
const base: CreatorPortfolio = {
  creatorId: 7,
  displayName: 'Layla Eats',
  bio: 'Food creator',
  foodCategory: 'Burgers',
  location: 'Beirut',
  priceMinCents: 5000,
  priceMaxCents: 20000,
  socialAccounts: [
    {
      platform: 'instagram',
      followers: 12000,
      engagementRate: 3.5,
      lastPulledAt: '2026-11-01T12:00:00Z',
    },
  ],
  featuredCampaigns: [
    { campaignId: 2, name: 'Burger launch', verified: true, redemptions: 4, roiPercent: 35 },
  ],
  averageRating: 4.5,
  reviewCount: 1,
  reviews: [{ rating: 5, text: 'Excellent', createdAt: '2026-11-02T12:00:00Z' }],
}
function open(data: CreatorPortfolio) {
  fetchMock.mockResolvedValue(data)
  render(
    <MemoryRouter initialEntries={['/brand/creators/7/portfolio']}>
      <Routes>
        <Route path="/brand/creators/:creatorId/portfolio" element={<CreatorPortfolioPage />} />
      </Routes>
    </MemoryRouter>,
  )
}
beforeEach(() => fetchMock.mockReset())
describe('EEC-35 creator portfolio', () => {
  it('TC-F1.1.1 shows profile, social metrics, verified campaign ROI and reviews', async () => {
    open(base)
    expect(await screen.findByRole('heading', { name: 'Layla Eats' })).toBeInTheDocument()
    expect(screen.getByText(/Followers:\s*12,000/)).toBeInTheDocument()
    expect(screen.getByText('Verified result')).toBeInTheDocument()
    expect(screen.getByText('ROI: 35%')).toBeInTheDocument()
    expect(screen.getByText('Excellent')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/brand/creators/7/portfolio')
  })
  it('TC-F1.1.2 does not label a zero-redemption campaign verified', async () => {
    open({
      ...base,
      featuredCampaigns: [
        { campaignId: 2, name: 'Burger launch', verified: false, redemptions: 0, roiPercent: null },
      ],
    })
    expect(await screen.findByText('Not verified')).toBeInTheDocument()
    expect(screen.queryByText('Verified result')).not.toBeInTheDocument()
    expect(screen.getByText('ROI: Not available')).toBeInTheDocument()
  })
  it('TC-F1.1.3 shows campaign and review empty states without hiding profile', async () => {
    open({ ...base, featuredCampaigns: [], reviews: [], reviewCount: 0, averageRating: null })
    expect(await screen.findByText('No completed featured campaigns yet.')).toBeInTheDocument()
    expect(screen.getByText('No reviews yet.')).toBeInTheDocument()
    expect(screen.getByText('Beirut')).toBeInTheDocument()
  })
})
