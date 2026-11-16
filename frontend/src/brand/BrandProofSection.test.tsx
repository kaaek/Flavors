import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { apiFetch } from '../api/client'
import type { DeliveryProof, ProofSubmission } from '../api/proof'
import { BrandProofSection } from './BrandProofSection'
import { CampaignDetailPage } from './CampaignDetailPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))

const apiFetchMock = vi.mocked(apiFetch)

function version(round: number, overrides: Partial<ProofSubmission> = {}): ProofSubmission {
  return {
    id: round,
    round,
    status: 'SUBMITTED',
    createdAt: '2030-01-16T10:00:00Z',
    submittedAt: '2030-01-16T12:05:00Z',
    links: [`https://www.instagram.com/reel/round-${round}/`],
    files: [],
    ...overrides,
  }
}

function proof(overrides: Partial<DeliveryProof> = {}): DeliveryProof {
  return {
    campaignId: 42,
    campaignName: 'Summer menu launch',
    campaignStatus: 'PROOF_SUBMITTED',
    reviewDeadline: '2030-01-21T12:05:00Z',
    submissions: [],
    ...overrides,
  }
}

function renderSection() {
  render(
    <BrandProofSection campaignId={42} creatorName="Nour Haddad" lastPostingDate="2030-01-30" />,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('says when nothing has been sent yet', async () => {
  apiFetchMock.mockResolvedValueOnce(proof({ campaignStatus: 'ACTIVE', reviewDeadline: null }))
  renderSection()

  expect(
    await screen.findByText(
      "Nour Haddad hasn't sent the proof yet. The last posting date is Jan 30, 2030.",
    ),
  ).toBeInTheDocument()
  expect(apiFetchMock).toHaveBeenCalledWith('/api/brand/campaigns/42/proof')
})

it('shows every sent version, newest first and open, with its links and screenshots', async () => {
  const screenshot = {
    id: 8,
    contentType: 'image/png',
    sizeBytes: 100,
    downloadUrl: '/api/proof-files/8',
  }
  apiFetchMock.mockResolvedValueOnce(
    proof({ submissions: [version(1), version(2, { files: [screenshot] })] }),
  )
  renderSection()

  const rounds = await screen.findAllByText(/^Round \d$/)
  expect(rounds.map((r) => r.textContent)).toEqual(['Round 2', 'Round 1'])
  const newest = rounds[0].closest('details') as HTMLDetailsElement
  expect(newest.open).toBe(true)
  expect((rounds[1].closest('details') as HTMLDetailsElement).open).toBe(false)
  expect(within(newest).getByRole('link', { name: /round-2/ })).toHaveAttribute(
    'href',
    'https://www.instagram.com/reel/round-2/',
  )
  expect(within(newest).getByRole('img', { name: 'Screenshot 1' })).toHaveAttribute(
    'src',
    '/api/proof-files/8',
  )
})

it('never turns a link that is not a web address into a clickable link', async () => {
  apiFetchMock.mockResolvedValueOnce(
    proof({ submissions: [version(1, { links: ['javascript:alert(1)'] })] }),
  )
  renderSection()

  expect(await screen.findByText('javascript:alert(1)')).toBeInTheDocument()
  expect(screen.queryByRole('link')).not.toBeInTheDocument()
})

it('waits for a new version while corrections are requested', async () => {
  apiFetchMock.mockResolvedValueOnce(
    proof({ campaignStatus: 'CORRECTIONS_REQUESTED', submissions: [version(1)] }),
  )
  renderSection()

  expect(await screen.findByText('Waiting for a new version from Nour Haddad.')).toBeInTheDocument()
  expect((screen.getByText('Round 1').closest('details') as HTMLDetailsElement).open).toBe(false)
})

const campaign: Campaign = {
  id: 42,
  name: 'Summer menu launch',
  status: 'PROOF_SUBMITTED',
  creator: { id: 3, displayName: 'Nour Haddad' },
  branches: [{ id: 11, name: 'Hamra', location: 'Beirut' }],
  priceOfferedCents: 50000,
  startDate: '2030-01-10',
  lastPostingDate: '2030-01-30',
  deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
  contentRequirements: null,
  sentAt: '2030-01-01T10:00:00Z',
  responseDeadline: '2030-01-04T10:00:00Z',
  agreedPriceCents: 50000,
  paymentDeadline: '2030-01-05T10:00:00Z',
  acceptedAt: '2030-01-02T10:00:00Z',
  escrowStatus: 'HELD',
  createdAt: '2030-01-01T09:00:00Z',
  updatedAt: '2030-01-02T10:00:00Z',
}

function renderPage(status: Campaign['status']) {
  apiFetchMock.mockImplementation(async (path: string) =>
    path.endsWith('/proof') ? proof({ submissions: [version(1)] }) : { ...campaign, status },
  )
  render(
    <MemoryRouter initialEntries={['/brand/campaigns/42']}>
      <Routes>
        <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

it('shows the proof on the campaign page once it is sent', async () => {
  renderPage('PROOF_SUBMITTED')

  expect(await screen.findByRole('heading', { name: 'Delivery proof' })).toBeInTheDocument()
  expect(await screen.findByText('Round 1')).toBeInTheDocument()
})

it('has no proof section before the campaign is paid', async () => {
  renderPage('AWAITING_PAYMENT')

  expect(await screen.findByRole('heading', { name: 'Awaiting payment' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Delivery proof' })).not.toBeInTheDocument()
  expect(apiFetchMock).not.toHaveBeenCalledWith('/api/brand/campaigns/42/proof')
})
