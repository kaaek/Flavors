import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { apiFetch } from '../api/client'
import { PROOF_REFRESH_MS, type DeliveryProof, type ProofSubmission } from '../api/proof'
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
    screenshotsRequired: false,
    submissions: [],
    ...overrides,
  }
}

function renderSection() {
  const onChanged = vi.fn()
  render(
    <BrandProofSection
      campaignId={42}
      creatorName="Nour Haddad"
      lastPostingDate="2030-01-30"
      onChanged={onChanged}
    />,
  )
  return onChanged
}

/** A server answer the test releases when it wants, to check what happens to an answer that arrives late. */
function later<T>() {
  let resolve: (value: T) => void = () => undefined
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

// The proof checks are run by hand; every other timer (Testing Library's own) runs as usual.
let refreshes: Array<() => void> = []
const realSetInterval = window.setInterval.bind(window)

beforeEach(() => {
  apiFetchMock.mockReset()
  refreshes = []
  vi.spyOn(window, 'setInterval').mockImplementation(((
    handler: () => void,
    delay?: number,
    ...rest: unknown[]
  ) => {
    if (delay === PROOF_REFRESH_MS) {
      refreshes.push(handler)
      return 0
    }
    return realSetInterval(handler, delay, ...rest)
  }) as typeof window.setInterval)
})

afterEach(() => {
  vi.restoreAllMocks()
})

/** Runs the section's next check. Its timer starts in an effect, which can run just after the text shows. */
async function checkForNewProof() {
  await waitFor(() => expect(refreshes.length).toBeGreaterThan(0))
  await act(async () => refreshes[refreshes.length - 1]())
}

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
  screenshotsRequired: false,
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

// FR-F5.5
it('shows a version the creator sends within a minute, without a reload', async () => {
  apiFetchMock
    .mockResolvedValueOnce(proof({ campaignStatus: 'ACTIVE', reviewDeadline: null }))
    .mockResolvedValueOnce(proof({ submissions: [version(1)] }))
  const onChanged = renderSection()

  expect(await screen.findByText(/hasn't sent the proof yet/)).toBeInTheDocument()
  expect(screen.getByText(/updates by itself/)).toBeInTheDocument()
  expect(PROOF_REFRESH_MS).toBeLessThanOrEqual(60_000)
  await waitFor(() => expect(refreshes).toHaveLength(1))

  await checkForNewProof()

  expect(await screen.findByText('Round 1')).toBeInTheDocument()
  expect(apiFetchMock).toHaveBeenLastCalledWith('/api/brand/campaigns/42/proof')
  // The status moved on (ACTIVE to PROOF_SUBMITTED), so the page reloads its header.
  expect(onChanged).toHaveBeenCalledOnce()
})

it('stops checking once a version waits for review', async () => {
  apiFetchMock.mockResolvedValueOnce(proof({ submissions: [version(1)] }))
  renderSection()

  expect(await screen.findByText('Round 1')).toBeInTheDocument()
  expect(refreshes).toHaveLength(0)
  expect(screen.queryByText(/updates by itself/)).not.toBeInTheDocument()
})

it('keeps checking while corrections are requested, without reloading the page for nothing', async () => {
  apiFetchMock.mockResolvedValue(
    proof({ campaignStatus: 'CORRECTIONS_REQUESTED', submissions: [version(1)] }),
  )
  const onChanged = renderSection()
  expect(await screen.findByText('Waiting for a new version from Nour Haddad.')).toBeInTheDocument()

  await checkForNewProof()

  expect(apiFetchMock).toHaveBeenCalledTimes(2)
  expect(onChanged).not.toHaveBeenCalled()
})

const otherCampaign: Campaign = {
  ...campaign,
  id: 43,
  name: 'Winter menu launch',
  status: 'ACTIVE',
  creator: null,
}

/** The brand's campaign page, with a link to another campaign (as a notification could give). */
function renderPageWithLinkTo43() {
  render(
    <MemoryRouter initialEntries={['/brand/campaigns/42']}>
      <Link to="/brand/campaigns/43">Open the other campaign</Link>
      <Routes>
        <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

it('shows only the open campaign after navigating, not what was refreshed for the previous one', async () => {
  let checks42 = 0
  apiFetchMock.mockImplementation(async (path: string) => {
    switch (path) {
      case '/api/brand/campaigns/42':
        return { ...campaign, status: checks42 === 0 ? 'ACTIVE' : 'PROOF_SUBMITTED' }
      case '/api/brand/campaigns/42/proof':
        return checks42++ === 0
          ? proof({ campaignStatus: 'ACTIVE', reviewDeadline: null })
          : proof({ submissions: [version(1)] })
      case '/api/brand/campaigns/43':
        return otherCampaign
      case '/api/brand/campaigns/43/proof':
        return proof({ campaignId: 43, campaignStatus: 'ACTIVE', reviewDeadline: null })
      default:
        throw new Error(`Unexpected call to ${path}`)
    }
  })
  renderPageWithLinkTo43()
  expect(await screen.findByText(/Nour Haddad hasn't sent the proof yet/)).toBeInTheDocument()

  await checkForNewProof()
  expect(await screen.findByText('Round 1')).toBeInTheDocument()
  expect(await screen.findByText('Proof submitted')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('link', { name: 'Open the other campaign' }))

  expect(await screen.findByRole('heading', { name: 'Winter menu launch' })).toBeInTheDocument()
  expect(await screen.findByText(/The creator hasn't sent the proof yet/)).toBeInTheDocument()
  expect(screen.queryByText('Round 1')).not.toBeInTheDocument()
  expect(screen.queryByText('Proof submitted')).not.toBeInTheDocument()
})

it('ignores a check about the previous campaign that answers after navigating', async () => {
  const lateCheck = later<DeliveryProof>()
  let checks42 = 0
  apiFetchMock.mockImplementation(async (path: string) => {
    switch (path) {
      case '/api/brand/campaigns/42':
        return { ...campaign, status: 'ACTIVE' }
      case '/api/brand/campaigns/42/proof':
        return checks42++ === 0
          ? proof({ campaignStatus: 'ACTIVE', reviewDeadline: null })
          : lateCheck.promise
      case '/api/brand/campaigns/43':
        return otherCampaign
      case '/api/brand/campaigns/43/proof':
        return proof({ campaignId: 43, campaignStatus: 'ACTIVE', reviewDeadline: null })
      default:
        throw new Error(`Unexpected call to ${path}`)
    }
  })
  renderPageWithLinkTo43()
  expect(await screen.findByText(/Nour Haddad hasn't sent the proof yet/)).toBeInTheDocument()

  await checkForNewProof()
  fireEvent.click(screen.getByRole('link', { name: 'Open the other campaign' }))
  expect(await screen.findByText(/The creator hasn't sent the proof yet/)).toBeInTheDocument()
  await act(async () => lateCheck.resolve(proof({ submissions: [version(1)] })))

  expect(screen.queryByText('Round 1')).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Winter menu launch' })).toBeInTheDocument()
  // The late answer did not make the page reload campaign 42 either.
  expect(
    apiFetchMock.mock.calls.filter(([path]) => path === '/api/brand/campaigns/42'),
  ).toHaveLength(1)
})

it('has no proof section before the campaign is paid', async () => {
  renderPage('AWAITING_PAYMENT')

  expect(await screen.findByRole('heading', { name: 'Awaiting payment' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Delivery proof' })).not.toBeInTheDocument()
  expect(apiFetchMock).not.toHaveBeenCalledWith('/api/brand/campaigns/42/proof')
})
