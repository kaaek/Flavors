import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { getEscrow, showsEscrow, type Escrow, type PayoutStatus } from '../api/payments'
import { EscrowPanel } from './EscrowPanel'

vi.mock('../api/payments', async (original) => ({
  ...(await original<typeof import('../api/payments')>()),
  getEscrow: vi.fn(),
}))
const load = vi.mocked(getEscrow)
const base: Escrow = {
  campaignId: 42,
  status: 'HELD',
  amountCents: 20003,
  heldAt: null,
  releasedAt: null,
  updatedAt: '2026-11-17T12:00:00Z',
  feeCents: 3000,
  creatorNetCents: 17003,
  payoutStatus: 'AUTHORIZED',
  authorizationSource: 'APPROVAL',
  authorizedAt: '2026-11-17T12:00:00Z',
  submittedAt: null,
  confirmedAt: null,
  simulated: null,
  payoutAvailable: true,
}
let tick: () => void
beforeEach(() => {
  load.mockReset()
  const originalInterval = window.setInterval.bind(window)
  vi.spyOn(window, 'setInterval').mockImplementation(((callback: TimerHandler, delay?: number) => {
    if (delay === 30_000) {
      tick = callback as () => void
      return 1
    }
    return originalInterval(callback, delay)
  }) as typeof window.setInterval)
})
afterEach(() => vi.restoreAllMocks())
const panel = (id = 42) => (
  <EscrowPanel campaignId={id} viewer="brand" fallbackStatus="HELD" refreshKey="1" />
)

it.each<[PayoutStatus, string]>([
  ['AUTHORIZED', 'Release authorized — payout pending.'],
  ['SUBMITTED', 'Payout submitted — awaiting confirmation.'],
  ['UNKNOWN', 'Payout outcome unknown — awaiting reconciliation.'],
  ['FAILED', 'Payout failed. Funds remain held; operational follow-up is required.'],
])('shows %s with stored fee/net and no premature success', async (payoutStatus, message) => {
  load.mockResolvedValue({ ...base, payoutStatus })
  render(panel())
  expect(await screen.findByText(message)).toBeInTheDocument()
  expect(screen.getByText('$200.03')).toBeInTheDocument()
  expect(screen.getByText('$30.00')).toBeInTheDocument()
  expect(screen.getByText('$170.03')).toBeInTheDocument()
  expect(
    screen.queryByText(/creator paid|payment received|payout confirmed/i),
  ).not.toBeInTheDocument()
})

it('labels simulated confirmation and stops terminal polling', async () => {
  load.mockResolvedValue({
    ...base,
    status: 'RELEASED',
    payoutStatus: 'CONFIRMED',
    simulated: true,
    confirmedAt: '2026-11-17T12:01:00Z',
  })
  render(panel())
  expect(
    await screen.findByText('Simulated payout confirmed — escrow released.'),
  ).toBeInTheDocument()
  expect(screen.getByText(/Simulation only.*no real money/)).toBeInTheDocument()
  await act(async () => tick())
  expect(load).toHaveBeenCalledTimes(1)
})

it('shows a frozen rejection and the deferred Sprint 3 workflow', async () => {
  load.mockResolvedValue({
    ...base,
    status: 'FROZEN',
    payoutStatus: null,
    feeCents: null,
    creatorNetCents: null,
  })
  render(panel())
  expect(await screen.findByText(/Escrow is FROZEN.*Sprint 3 EEC-54/)).toBeInTheDocument()
  expect(screen.queryByText('Flavors fee (15%)')).not.toBeInTheDocument()
})

it('refreshes an unanswered held payment into automatic Timer C authorization', async () => {
  load
    .mockResolvedValueOnce({ ...base, payoutStatus: null, feeCents: null, creatorNetCents: null })
    .mockResolvedValue({ ...base, authorizationSource: 'TIMER_C', payoutAvailable: false })
  render(panel())
  expect(await screen.findByText(/No release operation is recorded/)).toBeInTheDocument()
  await act(async () => tick())
  expect(await screen.findByText('Release authorized — payout pending.')).toBeInTheDocument()
  expect(screen.getByText(/automatically after the five-day/)).toBeInTheDocument()
  expect(screen.getByText(/execution is currently unavailable/)).toBeInTheDocument()
})

it('isolates late responses after changing campaign and viewer', async () => {
  let finish!: (value: Escrow) => void
  load
    .mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    .mockResolvedValue({ ...base, campaignId: 43, amountCents: 40000, payoutStatus: null })
  const view = render(panel())
  view.rerender(
    <EscrowPanel campaignId={43} viewer="creator" fallbackStatus="HELD" refreshKey="1" />,
  )
  expect(await screen.findByText('$400.00')).toBeInTheDocument()
  await act(async () =>
    finish({ ...base, status: 'RELEASED', payoutStatus: 'CONFIRMED', simulated: true }),
  )
  expect(screen.queryByText('$200.03')).not.toBeInTheDocument()
  expect(screen.queryByText(/Simulated payout confirmed/)).not.toBeInTheDocument()
  expect(load).toHaveBeenLastCalledWith('creator', 43)
})

it('retains a known state with a warning on refresh failure and prevents overlapping requests', async () => {
  let finish!: (value: Escrow) => void
  load.mockReturnValueOnce(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  render(panel())
  await act(async () => tick())
  expect(load).toHaveBeenCalledTimes(1)
  await act(async () => finish(base))
  load.mockRejectedValueOnce(new Error('network'))
  await act(async () => tick())
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not refresh payout details')
  expect(screen.getByText('Release authorized — payout pending.')).toBeInTheDocument()
})

it('keeps released, frozen and refunded escrow visible without showing unpaid expired campaigns', () => {
  expect(showsEscrow('COMPLETED', 'RELEASED')).toBe(true)
  expect(showsEscrow('PROOF_SUBMITTED', 'FROZEN')).toBe(true)
  expect(showsEscrow('COMPLETED', 'REFUNDED')).toBe(true)
  expect(showsEscrow('EXPIRED', 'PENDING')).toBe(false)
  expect(showsEscrow('ACTIVE', null)).toBe(false)
})
