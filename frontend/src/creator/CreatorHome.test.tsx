import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { CreatorHome } from './CreatorHome'
import { CreatorStatusPage } from './CreatorStatusPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

function answers(status: unknown, social: unknown) {
  apiFetchMock.mockImplementation((path: string) =>
    Promise.resolve(path === '/api/creator/status' ? status : social),
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('tells a pending creator she is waiting and asks her to connect Instagram', async () => {
  answers({ verificationStatus: 'PENDING_APPROVAL', rejectionReason: null }, [])
  render(
    <MemoryRouter>
      <CreatorHome />
    </MemoryRouter>,
  )

  expect(await screen.findByText('Your profile is waiting for approval')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Connect Instagram' })).toHaveAttribute(
    'href',
    '/creator/social',
  )
  // One click to every creator page.
  expect(screen.getByRole('link', { name: /Manage portfolio/ })).toHaveAttribute(
    'href',
    '/creator/portfolio',
  )
  expect(screen.getByRole('link', { name: /Profile status/ })).toHaveAttribute(
    'href',
    '/creator/status',
  )
})

it('sums the followers of her connected accounts and has nothing to nag about', async () => {
  answers({ verificationStatus: 'APPROVED', rejectionReason: null }, [
    {
      id: 1,
      platform: 'instagram',
      status: 'CONNECTED',
      followers: 12000,
      engagementRate: 3,
      lastPulledAt: '2026-11-04T03:00:00Z',
    },
  ])
  render(
    <MemoryRouter>
      <CreatorHome />
    </MemoryRouter>,
  )

  expect(await screen.findByText('12,000')).toBeInTheDocument()
  expect(screen.queryByText('Connect your Instagram account')).not.toBeInTheDocument()
})

it('still asks her to connect when the only account is a typed username with no pulled numbers', async () => {
  answers({ verificationStatus: 'APPROVED', rejectionReason: null }, [
    {
      id: 1,
      platform: 'instagram',
      status: 'CONNECTED',
      followers: null,
      engagementRate: null,
      lastPulledAt: null,
    },
  ])
  render(
    <MemoryRouter>
      <CreatorHome />
    </MemoryRouter>,
  )

  expect(await screen.findByText('Connect your Instagram account')).toBeInTheDocument()
})

it('shows why a profile was not approved', async () => {
  apiFetchMock.mockResolvedValue({
    verificationStatus: 'REJECTED',
    rejectionReason: 'Photos too blurry',
  })
  render(
    <MemoryRouter>
      <CreatorStatusPage />
    </MemoryRouter>,
  )

  expect(await screen.findByText('Your profile was not approved')).toBeInTheDocument()
  expect(screen.getByText(/Photos too blurry/)).toBeInTheDocument()
})
