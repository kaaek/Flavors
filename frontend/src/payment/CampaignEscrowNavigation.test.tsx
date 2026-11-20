import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import type { Campaign } from '../api/campaigns'
import type { Escrow } from '../api/payments'
import { CampaignDetailPage } from '../brand/CampaignDetailPage'
import { CreatorCampaignDetailPage } from '../creator/CreatorCampaignDetailPage'

vi.mock('../api/client', async (original) => ({
  ...(await original<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
vi.mock('../brand/BrandProofSection', () => ({ BrandProofSection: () => null }))
vi.mock('../creator/CreatorProofSection', () => ({ CreatorProofSection: () => null }))
const api = vi.mocked(apiFetch)
const campaign = (id: number): Campaign => ({
  id,
  name: `Campaign ${id}`,
  status: 'PROOF_SUBMITTED',
  creator: { id: 3, displayName: 'Creator' },
  branches: [],
  priceOfferedCents: 20000,
  agreedPriceCents: 20000,
  startDate: null,
  lastPostingDate: null,
  deliverables: [],
  contentRequirements: null,
  screenshotsRequired: false,
  sentAt: null,
  responseDeadline: null,
  paymentDeadline: null,
  acceptedAt: null,
  escrowStatus: 'HELD',
  createdAt: '2026-11-17T12:00:00Z',
  updatedAt: '2026-11-17T12:00:00Z',
})
const escrow = (id: number): Escrow => ({
  campaignId: id,
  status: 'HELD',
  amountCents: id === 42 ? 20000 : 40000,
  heldAt: null,
  releasedAt: null,
  updatedAt: '2026-11-17T12:00:00Z',
  feeCents: null,
  creatorNetCents: null,
  payoutStatus: null,
  authorizationSource: null,
  authorizedAt: null,
  submittedAt: null,
  confirmedAt: null,
  simulated: null,
  payoutAvailable: false,
  payoutNeedsReview: false,
})
beforeEach(() => {
  api.mockReset()
})

it.each(['brand', 'creator'] as const)(
  '%s navigation never shows the previous campaign or its late payout response',
  async (viewer) => {
    let finish!: (response: Escrow) => void
    api.mockImplementation((async (path: string) => {
      const id = path.includes('/43') ? 43 : 42
      if (path.endsWith('/escrow')) {
        return id === 42
          ? new Promise<Escrow>((resolve) => {
              finish = resolve
            })
          : escrow(id)
      }
      return campaign(id)
    }) as typeof apiFetch)
    render(
      <MemoryRouter initialEntries={[`/${viewer}/campaigns/42`]}>
        <Link to={`/${viewer}/campaigns/43`}>Next campaign</Link>
        <Routes>
          <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
          <Route path="/creator/campaigns/:id" element={<CreatorCampaignDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { name: 'Campaign 42' })).toBeInTheDocument()
    fireEvent.click(screen.getByText('Next campaign'))
    expect(await screen.findByRole('heading', { name: 'Campaign 43' })).toBeInTheDocument()
    expect(await screen.findByText('$400.00')).toBeInTheDocument()
    await act(async () =>
      finish({ ...escrow(42), status: 'RELEASED', payoutStatus: 'CONFIRMED', simulated: true }),
    )
    expect(screen.queryByRole('heading', { name: 'Campaign 42' })).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: 'Payment and payout' })).queryByText('$200.00'),
    ).not.toBeInTheDocument()
    expect(screen.queryByText(/Simulated payout confirmed/)).not.toBeInTheDocument()
  },
)
