import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { apiFetch } from '../api/client'
import { redirectTo } from '../lib/redirect'
import { CampaignDetailPage } from './CampaignDetailPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
vi.mock('../lib/redirect', () => ({ redirectTo: vi.fn() }))

const apiFetchMock = vi.mocked(apiFetch)
const redirectMock = vi.mocked(redirectTo)

const awaiting: Campaign = {
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
  sentAt: '2030-01-01T10:00:00Z',
  responseDeadline: '2030-01-04T10:00:00Z',
  agreedPriceCents: 65000,
  paymentDeadline: '2030-01-05T10:00:00Z',
  acceptedAt: '2030-01-02T10:00:00Z',
  escrowStatus: null,
  createdAt: '2030-01-01T09:00:00Z',
  updatedAt: '2030-01-02T10:00:00Z',
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
  redirectMock.mockReset()
})

it('shows Awaiting payment with the time left and the amount to pay (TC-F7.1.5)', async () => {
  apiFetchMock.mockResolvedValue(awaiting)
  renderAt('/brand/campaigns/42')

  expect(await screen.findByRole('heading', { name: 'Awaiting payment' })).toBeInTheDocument()
  expect(screen.getByText(/left to pay/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Pay $650.00' })).toBeEnabled()
})

it('starts the payment with a new Idempotency-Key and sends the payer to the checkout', async () => {
  apiFetchMock.mockResolvedValueOnce(awaiting).mockResolvedValueOnce({
    checkoutUrl: '/payments/simulated/abc',
    amountCents: 65000,
    currency: 'USD',
    status: 'PENDING',
  })
  renderAt('/brand/campaigns/42')

  fireEvent.click(await screen.findByRole('button', { name: 'Pay $650.00' }))

  await waitFor(() => expect(redirectMock).toHaveBeenCalledWith('/payments/simulated/abc'))
  const [path, options] = apiFetchMock.mock.calls[1]
  expect(path).toBe('/api/brand/campaigns/42/payment')
  expect(options).toMatchObject({
    method: 'POST',
    headers: { 'Idempotency-Key': expect.any(String) },
  })
})

it('shows the refusal and keeps the campaign awaiting payment when the start fails', async () => {
  apiFetchMock.mockResolvedValueOnce(awaiting).mockRejectedValueOnce(
    Object.assign(new Error('Payments are not switched on for this environment.'), {
      name: 'ApiError',
    }),
  )
  renderAt('/brand/campaigns/42')

  fireEvent.click(await screen.findByRole('button', { name: 'Pay $650.00' }))

  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(redirectMock).not.toHaveBeenCalled()
  expect(screen.getByRole('heading', { name: 'Awaiting payment' })).toBeInTheDocument()
})

it('shows the escrow status once the payment is held', async () => {
  apiFetchMock.mockResolvedValue({
    ...awaiting,
    status: 'ACTIVE',
    escrowStatus: 'HELD',
  })
  renderAt('/brand/campaigns/42')

  expect(await screen.findByText('Held')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /^Pay / })).not.toBeInTheDocument()
})

it('confirms the payment after the payer comes back from the checkout', async () => {
  apiFetchMock.mockResolvedValue({ ...awaiting, status: 'ACTIVE', escrowStatus: 'HELD' })
  renderAt('/brand/campaigns/42?payment=held')

  expect(await screen.findByText(/Payment received/)).toBeInTheDocument()
})

it('tells the payer when the payment did not go through and that they can try again', async () => {
  apiFetchMock.mockResolvedValue(awaiting)
  renderAt('/brand/campaigns/42?payment=failed')

  expect(await screen.findByText(/The payment was not completed/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Pay $650.00' })).toBeEnabled()
})
