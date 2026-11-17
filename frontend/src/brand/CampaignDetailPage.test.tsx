import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { apiFetch } from '../api/client'
import { CampaignDetailPage } from './CampaignDetailPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

const campaign: Campaign = {
  id: 42,
  name: 'Summer menu launch',
  status: 'AWAITING_PAYMENT',
  creator: { id: 3, displayName: 'Nour Haddad' },
  branches: [{ id: 11, name: 'Hamra', location: 'Beirut' }],
  priceOfferedCents: 50000,
  startDate: '2030-01-10',
  lastPostingDate: '2030-01-30',
  deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
  contentRequirements: null,
  screenshotsRequired: false,
  sentAt: '2030-01-01T10:00:00Z',
  responseDeadline: '2030-01-04T10:00:00Z',
  agreedPriceCents: 65000,
  paymentDeadline: '2030-01-05T10:00:00Z',
  acceptedAt: '2030-01-02T10:00:00Z',
  escrowStatus: null,
  createdAt: '2030-01-01T09:00:00Z',
  updatedAt: '2030-01-02T10:00:00Z',
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/brand/campaigns/42']}>
      <Routes>
        <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('shows the agreed price, the payment deadline and when it was accepted next to the original price', async () => {
  apiFetchMock.mockResolvedValue(campaign)
  renderPage()

  expect(await screen.findByText('Agreed price')).toBeInTheDocument()
  expect(screen.getByText('$650.00')).toBeInTheDocument()
  expect(screen.getByText('$500.00')).toBeInTheDocument()
  expect(screen.getByText('Pay by')).toBeInTheDocument()
  expect(screen.getByText('Accepted')).toBeInTheDocument()
})

it('does not show agreed price, payment deadline or accepted rows before the creator accepts', async () => {
  apiFetchMock.mockResolvedValue({
    ...campaign,
    status: 'AWAITING_CREATOR_RESPONSE',
    agreedPriceCents: null,
    paymentDeadline: null,
    acceptedAt: null,
  })
  renderPage()

  expect(await screen.findByText('Price offered')).toBeInTheDocument()
  expect(screen.queryByText('Agreed price')).not.toBeInTheDocument()
  expect(screen.queryByText('Pay by')).not.toBeInTheDocument()
  expect(screen.queryByText('Accepted')).not.toBeInTheDocument()
})
