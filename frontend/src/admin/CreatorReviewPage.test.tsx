import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { CreatorReviewPage } from './CreatorReviewPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

const queue = [
  {
    id: 7,
    displayName: 'Lara',
    foodCategory: 'Street food',
    location: 'Beirut',
    registeredAt: '2026-10-31T10:00:00Z',
  },
]

function review(overrides: Record<string, unknown> = {}) {
  return {
    id: 7,
    email: 'lara@example.com',
    displayName: 'Lara',
    bio: null,
    foodCategory: 'Street food',
    location: 'Beirut',
    priceMinCents: 10000,
    priceMaxCents: 30000,
    verificationStatus: 'PENDING_APPROVAL',
    rejectionReason: null,
    needsReconnection: false,
    socialAccounts: [
      {
        platform: 'instagram',
        externalId: 'lara.eats',
        status: 'CONNECTED',
        followers: 12000,
        engagementRate: 4.25,
        lastPulledAt: null,
      },
    ],
    ...overrides,
  }
}

beforeEach(() => apiFetchMock.mockReset())

it('shows the details and approves a profile', async () => {
  const user = userEvent.setup()
  apiFetchMock
    .mockResolvedValueOnce(queue)
    .mockResolvedValueOnce(review())
    .mockResolvedValueOnce(review())
    .mockResolvedValueOnce([])
  render(<CreatorReviewPage />)

  await user.click(await screen.findByRole('button', { name: 'Lara' }))
  expect(await screen.findByText(/12000 followers/)).toBeInTheDocument()
  expect(screen.getByText('Price per campaign: $100.00 to $300.00')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Approve' }))

  expect(apiFetchMock).toHaveBeenCalledWith('/api/admin/creators/7/approve', { method: 'POST' })
  expect(await screen.findByText('Lara is approved.')).toBeInTheDocument()
})

it('blocks approval while the social account needs reconnection', async () => {
  const user = userEvent.setup()
  apiFetchMock
    .mockResolvedValueOnce(queue)
    .mockResolvedValueOnce(review({ needsReconnection: true }))
  render(<CreatorReviewPage />)

  await user.click(await screen.findByRole('button', { name: 'Lara' }))

  expect(await screen.findByText(/Needs reconnection/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled()
})

it('shows the server message when rejecting without a reason', async () => {
  const user = userEvent.setup()
  apiFetchMock
    .mockResolvedValueOnce(queue)
    .mockResolvedValueOnce(review())
    .mockRejectedValueOnce(
      new ApiError(400, { errors: { reason: 'A reason is required to reject a profile.' } }),
    )
  render(<CreatorReviewPage />)

  await user.click(await screen.findByRole('button', { name: 'Lara' }))
  await user.click(await screen.findByRole('button', { name: 'Reject' }))

  expect(await screen.findByText('A reason is required to reject a profile.')).toBeInTheDocument()
})
