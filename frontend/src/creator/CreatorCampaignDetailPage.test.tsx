import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { apiFetch } from '../api/client'
import { CreatorCampaignDetailPage } from './CreatorCampaignDetailPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))

const apiFetchMock = vi.mocked(apiFetch)

const waiting: Campaign = {
  id: 42,
  name: 'Summer menu launch',
  status: 'AWAITING_CREATOR_RESPONSE',
  creator: { id: 3, displayName: 'Nour Haddad' },
  branches: [{ id: 11, name: 'Hamra', location: 'Beirut' }],
  priceOfferedCents: 50000,
  startDate: '2030-01-10',
  lastPostingDate: '2030-01-30',
  deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
  contentRequirements: null,
  sentAt: '2030-01-01T10:00:00Z',
  responseDeadline: '2030-01-04T10:00:00Z',
  agreedPriceCents: null,
  paymentDeadline: null,
  acceptedAt: null,
  escrowStatus: null,
  createdAt: '2030-01-01T09:00:00Z',
  updatedAt: '2030-01-01T10:00:00Z',
}

let intervals: Array<() => void> = []

beforeEach(() => {
  apiFetchMock.mockReset()
  intervals = []
  vi.spyOn(window, 'setInterval').mockImplementation(((handler: () => void, delay: number) => {
    if (delay === 30_000) intervals.push(handler)
    return 1
  }) as unknown as typeof window.setInterval)
})

afterEach(() => {
  vi.restoreAllMocks()
})

it('keeps checking the brand payment after the creator accepts in the same session', async () => {
  apiFetchMock
    .mockResolvedValueOnce(waiting)
    .mockResolvedValueOnce(undefined)
    .mockResolvedValue({ ...waiting, status: 'ACTIVE', escrowStatus: 'HELD' })

  render(
    <MemoryRouter initialEntries={['/creator/campaigns/42']}>
      <Routes>
        <Route path="/creator/campaigns/:id" element={<CreatorCampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )

  fireEvent.click(await screen.findByRole('button', { name: 'Accept campaign' }))
  expect(await screen.findByText('Campaign accepted successfully.')).toBeInTheDocument()

  expect(intervals).toHaveLength(1)
  act(() => intervals[0]())

  expect(await screen.findByText('Held')).toBeInTheDocument()
})
