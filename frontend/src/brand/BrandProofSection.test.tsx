import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { ApiError, apiFetch } from '../api/client'
import { PROOF_REFRESH_MS, type DeliveryProof, type ProofSubmission } from '../api/proof'
import type { CampaignProofReviews, ProofReview } from '../api/proofReviews'
import { BrandProofSection } from './BrandProofSection'
import { CampaignDetailPage } from './CampaignDetailPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))

const apiFetchMock = vi.mocked(apiFetch)
// Payment polling is covered independently; these tests count the proof section's refresh timer.
vi.mock('../payment/EscrowPanel', () => ({ EscrowPanel: () => null }))

const PROOF = '/api/brand/campaigns/42/proof'
const REVIEWS = '/api/brand/campaigns/42/proof/reviews'

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
    reviewDeadline: '2099-01-21T12:05:00Z',
    screenshotsRequired: false,
    submissions: [],
    ...overrides,
  }
}

function review(overrides: Partial<ProofReview> = {}): ProofReview {
  return {
    id: 70,
    submissionId: 1,
    decision: 'APPROVED',
    reason: null,
    decidedAt: '2030-01-18T09:00:00Z',
    requiredFollowUp: 'RELEASE_PAYMENT',
    ...overrides,
  }
}

function reviews(overrides: Partial<CampaignProofReviews> = {}): CampaignProofReviews {
  return {
    campaignId: 42,
    campaignStatus: 'PROOF_SUBMITTED',
    correctionRoundsUsed: 0,
    reviewDeadline: '2099-01-21T12:05:00Z',
    latestSubmissionId: 1,
    agreement: {
      startDate: '2030-01-10',
      lastPostingDate: '2030-01-30',
      contentRequirements: 'Show the new lunch menu and tag the branch.',
      screenshotsRequired: true,
      deliverables: [{ platform: 'INSTAGRAM', type: 'REEL', count: 2 }],
    },
    availableDecisions: ['APPROVED', 'CORRECTIONS_REQUESTED', 'REJECTED'],
    reviews: [],
    ...overrides,
  }
}

type Options = { method?: string; body?: unknown }

/** Answers by "METHOD path", so the order of the page's requests doesn't matter. */
function serve(routes: Record<string, unknown>) {
  apiFetchMock.mockImplementation((async (path: string, options?: Options) => {
    const key = `${options?.method ?? 'GET'} ${path}`
    if (!(key in routes)) throw new Error(`Unexpected request: ${key}`)
    const answer = routes[key]
    return typeof answer === 'function' ? (answer as (o?: Options) => unknown)(options) : answer
  }) as typeof apiFetch)
}

function requestsTo(method: string, path: string) {
  return apiFetchMock.mock.calls
    .filter(([p, o]) => p === path && (o?.method ?? 'GET') === method)
    .map(([, o]) => o as Options)
}

function renderSection(onChanged = vi.fn()) {
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

/** A review that is waiting for the brand, and what the backend returns after the decision is saved. */
function awaitingDecision(after: Partial<CampaignProofReviews>, afterStatus: Campaign['status']) {
  let saved: ProofReview | null = null
  serve({
    [`GET ${PROOF}`]: () =>
      proof({ submissions: [version(1)], campaignStatus: saved ? afterStatus : 'PROOF_SUBMITTED' }),
    [`GET ${REVIEWS}`]: () => (saved ? reviews({ ...after, reviews: [saved] }) : reviews()),
    [`POST ${PROOF}/1/review`]: (options?: Options) => {
      const body = options?.body as { decision: ProofReview['decision']; reason: string | null }
      saved = review({ decision: body.decision, reason: body.reason })
      return saved
    },
  })
}

/** A server answer the test releases when it wants, to check what happens to an answer that arrives late. */
function later<T>() {
  let resolve: (value: T) => void = () => undefined
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

// The proof checks are run by hand; every other timer (Testing Library's own) runs as usual. Like the browser, a
// cleared check is gone, so only the section's current check can run.
const refreshes = new Map<number, () => void>()
let nextRefreshId = 1_000_000
const realSetInterval = window.setInterval.bind(window)
const realClearInterval = window.clearInterval.bind(window)

beforeEach(() => {
  apiFetchMock.mockReset()
  refreshes.clear()
  vi.spyOn(window, 'setInterval').mockImplementation(((
    handler: () => void,
    delay?: number,
    ...rest: unknown[]
  ) => {
    if (delay === PROOF_REFRESH_MS) {
      refreshes.set(nextRefreshId, handler)
      return nextRefreshId++
    }
    return realSetInterval(handler, delay, ...rest)
  }) as typeof window.setInterval)
  vi.spyOn(window, 'clearInterval').mockImplementation(((id?: number) => {
    if (id !== undefined && refreshes.delete(id)) return
    realClearInterval(id)
  }) as typeof window.clearInterval)
})

afterEach(() => {
  vi.restoreAllMocks()
})

/** Runs the section's current check. Its timer starts in an effect, which can run just after the text shows. */
async function checkForNewProof() {
  await waitFor(() => expect(refreshes.size).toBe(1))
  await act(async () => [...refreshes.values()][0]())
}

/** What the brand's view gets before the creator sends anything. */
function notSentYet(campaignId: number) {
  return {
    proof: proof({ campaignId, campaignStatus: 'ACTIVE', reviewDeadline: null }),
    reviews: reviews({
      campaignId,
      campaignStatus: 'ACTIVE',
      reviewDeadline: null,
      latestSubmissionId: null,
      availableDecisions: [],
    }),
  }
}

const corrections = review({
  decision: 'CORRECTIONS_REQUESTED',
  requiredFollowUp: 'RESUBMIT_PROOF',
})

it('says when nothing has been sent yet', async () => {
  serve({
    [`GET ${PROOF}`]: proof({ campaignStatus: 'ACTIVE', reviewDeadline: null }),
    [`GET ${REVIEWS}`]: reviews({
      campaignStatus: 'ACTIVE',
      latestSubmissionId: null,
      availableDecisions: [],
    }),
  })
  renderSection()

  expect(
    await screen.findByText(
      "Nour Haddad hasn't sent the proof yet. The last posting date is Jan 30, 2030.",
    ),
  ).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Review proof' })).not.toBeInTheDocument()
})

it('shows every sent version, newest first and open, with its links and screenshots', async () => {
  const screenshot = {
    id: 8,
    contentType: 'image/png',
    sizeBytes: 100,
    downloadUrl: '/api/proof-files/8',
  }
  serve({
    [`GET ${PROOF}`]: proof({ submissions: [version(1), version(2, { files: [screenshot] })] }),
    [`GET ${REVIEWS}`]: reviews({
      latestSubmissionId: 2,
      correctionRoundsUsed: 1,
      reviews: [review({ decision: 'CORRECTIONS_REQUESTED', reason: 'Tag the branch.' })],
    }),
  })
  renderSection()

  const rounds = await screen.findAllByText(/^Round \d$/)
  expect(rounds.map((r) => r.textContent)).toEqual(['Round 2', 'Round 1'])
  const newest = rounds[0].closest('details') as HTMLDetailsElement
  const older = rounds[1].closest('details') as HTMLDetailsElement
  expect(newest.open).toBe(true)
  expect(older.open).toBe(false)
  expect(within(newest).getByRole('link', { name: /round-2/ })).toHaveAttribute(
    'href',
    'https://www.instagram.com/reel/round-2/',
  )
  expect(within(newest).getByRole('img', { name: 'Screenshot 1' })).toHaveAttribute(
    'src',
    '/api/proof-files/8',
  )
  // The older round carries the brand's earlier decision and its reason.
  expect(within(older).getByText('Corrections requested')).toBeInTheDocument()
  expect(within(older).getByText('Tag the branch.')).toBeInTheDocument()
})

it('never turns a link that is not a web address into a clickable link', async () => {
  serve({
    [`GET ${PROOF}`]: proof({ submissions: [version(1, { links: ['javascript:alert(1)'] })] }),
    [`GET ${REVIEWS}`]: reviews({ availableDecisions: [] }),
  })
  renderSection()

  expect(await screen.findByText('javascript:alert(1)')).toBeInTheDocument()
  expect(screen.queryByRole('link')).not.toBeInTheDocument()
})

it('lets the brand check the agreement and approve, then says what approval means today', async () => {
  awaitingDecision({ availableDecisions: [], reviewDeadline: null }, 'PROOF_SUBMITTED')
  const onChanged = renderSection()

  const panel = (await screen.findByRole('heading', { name: 'Review proof' })).closest('section')!
  expect(within(panel).getByText('2 × Instagram reel')).toBeInTheDocument()
  expect(within(panel).getByText('Show the new lunch menu and tag the branch.')).toBeInTheDocument()
  expect(within(panel).getByText('Required with the proof')).toBeInTheDocument()
  expect(within(panel).getByRole('progressbar', { name: /Review by/ })).toBeInTheDocument()
  expect(within(panel).getByText('You can request corrections 2 more times.')).toBeInTheDocument()

  fireEvent.click(within(panel).getByRole('button', { name: 'Approve proof' }))
  const confirm = screen.getByRole('group', { name: 'Approve this proof?' })
  expect(requestsTo('POST', `${PROOF}/1/review`)).toHaveLength(0)
  fireEvent.click(within(confirm).getByRole('button', { name: 'Approve proof' }))

  expect(await screen.findByText(/You approved the proof on/)).toBeInTheDocument()
  expect(screen.getByText(/Payment release has been authorized/)).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Review proof' })).not.toBeInTheDocument()
  expect(requestsTo('POST', `${PROOF}/1/review`)[0].body).toEqual({
    decision: 'APPROVED',
    reason: null,
  })
  expect(onChanged).toHaveBeenCalledOnce()
})

it('will not reject without a reason, and sends the reason trimmed', async () => {
  awaitingDecision({ availableDecisions: [], reviewDeadline: null }, 'PROOF_SUBMITTED')
  renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Reject…' }))
  fireEvent.click(screen.getByRole('button', { name: 'Reject proof' }))
  expect(screen.getByText('Write a reason before rejecting.')).toBeInTheDocument()
  expect(screen.getByLabelText(/Reason/)).toHaveAttribute('aria-invalid', 'true')
  expect(requestsTo('POST', `${PROOF}/1/review`)).toHaveLength(0)

  fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: '  Never posted.  ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Reject proof' }))

  expect(await screen.findByText(/You rejected the proof on/)).toBeInTheDocument()
  expect(requestsTo('POST', `${PROOF}/1/review`)[0].body).toEqual({
    decision: 'REJECTED',
    reason: 'Never posted.',
  })
})

it('asks for corrections with an optional note, then waits for the new version', async () => {
  awaitingDecision(
    {
      campaignStatus: 'CORRECTIONS_REQUESTED',
      correctionRoundsUsed: 1,
      availableDecisions: [],
      reviewDeadline: null,
      reviews: [review({ decision: 'CORRECTIONS_REQUESTED', reason: null })],
    },
    'CORRECTIONS_REQUESTED',
  )
  const onChanged = renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Request corrections' }))
  const confirm = screen.getByRole('group', { name: 'Request corrections' })
  expect(within(confirm).getByText(/You can ask 1 more time after this/)).toBeInTheDocument()
  fireEvent.click(within(confirm).getByRole('button', { name: 'Request corrections' }))

  expect(await screen.findByText('Waiting for a new version from Nour Haddad.')).toBeInTheDocument()
  expect(requestsTo('POST', `${PROOF}/1/review`)[0].body).toEqual({
    decision: 'CORRECTIONS_REQUESTED',
    reason: null,
  })
  expect(onChanged).toHaveBeenCalledOnce()
})

it('offers only approve and reject once both correction requests are used', async () => {
  serve({
    [`GET ${PROOF}`]: proof({ submissions: [version(1), version(2), version(3)] }),
    [`GET ${REVIEWS}`]: reviews({
      correctionRoundsUsed: 2,
      latestSubmissionId: 3,
      availableDecisions: ['APPROVED', 'REJECTED'],
    }),
  })
  renderSection()

  expect(await screen.findByRole('button', { name: 'Approve proof' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Request corrections' })).not.toBeInTheDocument()
  expect(screen.getByText(/used both correction requests/)).toBeInTheDocument()
})

it("shows the server's answer when the review time ended in the meantime", async () => {
  serve({
    [`GET ${PROOF}`]: proof({ submissions: [version(1)] }),
    [`GET ${REVIEWS}`]: reviews(),
    [`POST ${PROOF}/1/review`]: () => {
      throw new ApiError(409, { detail: 'The five-day review period has ended.' })
    },
  })
  renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Approve proof' }))
  fireEvent.click(
    within(screen.getByRole('group', { name: 'Approve this proof?' })).getByRole('button', {
      name: 'Approve proof',
    }),
  )

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'The five-day review period has ended.',
  )
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
  serve({
    'GET /api/brand/campaigns/42': { ...campaign, status },
    [`GET ${PROOF}`]: proof({ campaignStatus: status, submissions: [version(1)] }),
    [`GET ${REVIEWS}`]: reviews({ campaignStatus: status }),
  })
  render(
    <MemoryRouter initialEntries={['/brand/campaigns/42']}>
      <Routes>
        <Route path="/brand/campaigns/:id" element={<CampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

it('shows the proof and its review on the campaign page once it is sent', async () => {
  renderPage('PROOF_SUBMITTED')

  expect(await screen.findByRole('heading', { name: 'Delivery proof' })).toBeInTheDocument()
  expect(await screen.findByRole('heading', { name: 'Review proof' })).toBeInTheDocument()
})

// FR-F5.5
it('shows a version the creator sends within a minute, without a reload', async () => {
  let sent = false
  serve({
    [`GET ${PROOF}`]: () => (sent ? proof({ submissions: [version(1)] }) : notSentYet(42).proof),
    [`GET ${REVIEWS}`]: () => (sent ? reviews() : notSentYet(42).reviews),
  })
  const onChanged = renderSection()

  expect(await screen.findByText(/hasn't sent the proof yet/)).toBeInTheDocument()
  expect(screen.getByText(/updates by itself/)).toBeInTheDocument()
  expect(PROOF_REFRESH_MS).toBeLessThanOrEqual(60_000)
  await waitFor(() => expect(refreshes.size).toBe(1))

  sent = true
  await checkForNewProof()

  expect(await screen.findByText('Round 1')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Approve proof' })).toBeInTheDocument()
  // The status moved on (ACTIVE to PROOF_SUBMITTED), so the page reloads its header.
  expect(onChanged).toHaveBeenCalledOnce()
})

it('stops checking once nothing can change any more', async () => {
  serve({
    [`GET ${PROOF}`]: proof({
      campaignStatus: 'COMPLETED',
      reviewDeadline: null,
      submissions: [version(1)],
    }),
    [`GET ${REVIEWS}`]: reviews({
      campaignStatus: 'COMPLETED',
      reviewDeadline: null,
      availableDecisions: [],
      reviews: [review()],
    }),
  })
  renderSection()

  expect(await screen.findByText('Round 1')).toBeInTheDocument()
  expect(refreshes.size).toBe(0)
  expect(screen.queryByText(/updates by itself/)).not.toBeInTheDocument()
})

// FR-F5.5: a brand page left open stays current when the review continues in another session.
it('keeps an open page current when another session asks for corrections and the creator resends', async () => {
  let stage: 'review' | 'corrections' | 'resent' = 'review'
  serve({
    [`GET ${PROOF}`]: () =>
      stage === 'review'
        ? proof({ submissions: [version(1)] })
        : stage === 'corrections'
          ? proof({
              campaignStatus: 'CORRECTIONS_REQUESTED',
              reviewDeadline: null,
              submissions: [version(1)],
            })
          : proof({ submissions: [version(1), version(2)] }),
    [`GET ${REVIEWS}`]: () =>
      stage === 'review'
        ? reviews()
        : stage === 'corrections'
          ? reviews({
              campaignStatus: 'CORRECTIONS_REQUESTED',
              correctionRoundsUsed: 1,
              reviewDeadline: null,
              availableDecisions: [],
              reviews: [corrections],
            })
          : reviews({ correctionRoundsUsed: 1, latestSubmissionId: 2, reviews: [corrections] }),
  })
  const onChanged = renderSection()
  expect(await screen.findByRole('button', { name: 'Approve proof' })).toBeInTheDocument()

  // Another tab or session asks for corrections: this page never sent a request.
  stage = 'corrections'
  await checkForNewProof()
  expect(await screen.findByText('Waiting for a new version from Nour Haddad.')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Review proof' })).not.toBeInTheDocument()
  expect(onChanged).toHaveBeenCalledTimes(1)

  // The creator sends the corrected version from her own session.
  stage = 'resent'
  await checkForNewProof()
  expect(await screen.findByText('Round 2')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Approve proof' })).toBeEnabled()
  expect(onChanged).toHaveBeenCalledTimes(2)
  expect(requestsTo('POST', `${PROOF}/1/review`)).toHaveLength(0)
})

it('keeps checking while corrections are requested, without reloading the page for nothing', async () => {
  serve({
    [`GET ${PROOF}`]: proof({
      campaignStatus: 'CORRECTIONS_REQUESTED',
      reviewDeadline: null,
      submissions: [version(1)],
    }),
    [`GET ${REVIEWS}`]: reviews({
      campaignStatus: 'CORRECTIONS_REQUESTED',
      correctionRoundsUsed: 1,
      reviewDeadline: null,
      availableDecisions: [],
      reviews: [corrections],
    }),
  })
  const onChanged = renderSection()
  expect(await screen.findByText('Waiting for a new version from Nour Haddad.')).toBeInTheDocument()

  await checkForNewProof()

  expect(requestsTo('GET', PROOF)).toHaveLength(2)
  expect(onChanged).not.toHaveBeenCalled()
})

it('offers the review controls again when the creator sends the corrected version', async () => {
  let stage: 'review' | 'corrections' | 'resent' = 'review'
  serve({
    [`GET ${PROOF}`]: () =>
      stage === 'review'
        ? proof({ submissions: [version(1)] })
        : stage === 'corrections'
          ? proof({
              campaignStatus: 'CORRECTIONS_REQUESTED',
              reviewDeadline: null,
              submissions: [version(1)],
            })
          : proof({ submissions: [version(1), version(2)] }),
    [`GET ${REVIEWS}`]: () =>
      stage === 'review'
        ? reviews()
        : stage === 'corrections'
          ? reviews({
              campaignStatus: 'CORRECTIONS_REQUESTED',
              correctionRoundsUsed: 1,
              reviewDeadline: null,
              availableDecisions: [],
              reviews: [corrections],
            })
          : reviews({ correctionRoundsUsed: 1, latestSubmissionId: 2, reviews: [corrections] }),
    [`POST ${PROOF}/1/review`]: () => {
      stage = 'corrections'
      return corrections
    },
  })
  renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Request corrections' }))
  fireEvent.click(
    within(screen.getByRole('group', { name: 'Request corrections' })).getByRole('button', {
      name: 'Request corrections',
    }),
  )
  expect(await screen.findByText('Waiting for a new version from Nour Haddad.')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Review proof' })).not.toBeInTheDocument()

  stage = 'resent'
  await checkForNewProof()

  // The decision on version 1 must not hide the controls for version 2.
  expect(await screen.findByRole('heading', { name: 'Review proof' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Approve proof' })).toBeEnabled()
  expect(screen.getByText('You can request corrections 1 more time.')).toBeInTheDocument()
})

const otherCampaign: Campaign = {
  ...campaign,
  id: 43,
  name: 'Winter menu launch',
  status: 'ACTIVE',
  creator: null,
}

const campaign43Routes = {
  'GET /api/brand/campaigns/43': otherCampaign,
  'GET /api/brand/campaigns/43/proof': notSentYet(43).proof,
  'GET /api/brand/campaigns/43/proof/reviews': notSentYet(43).reviews,
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
  let sent = false
  serve({
    'GET /api/brand/campaigns/42': () => ({
      ...campaign,
      status: sent ? 'PROOF_SUBMITTED' : 'ACTIVE',
    }),
    [`GET ${PROOF}`]: () => (sent ? proof({ submissions: [version(1)] }) : notSentYet(42).proof),
    [`GET ${REVIEWS}`]: () => (sent ? reviews() : notSentYet(42).reviews),
    ...campaign43Routes,
  })
  renderPageWithLinkTo43()
  expect(await screen.findByText(/Nour Haddad hasn't sent the proof yet/)).toBeInTheDocument()

  sent = true
  await checkForNewProof()
  expect(await screen.findByText('Round 1')).toBeInTheDocument()
  expect(await screen.findByText('Proof submitted')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('link', { name: 'Open the other campaign' }))

  expect(await screen.findByRole('heading', { name: 'Winter menu launch' })).toBeInTheDocument()
  expect(await screen.findByText(/The creator hasn't sent the proof yet/)).toBeInTheDocument()
  expect(screen.queryByText('Round 1')).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Review proof' })).not.toBeInTheDocument()
  expect(screen.queryByText('Proof submitted')).not.toBeInTheDocument()
})

it('ignores a check about the previous campaign that answers after navigating', async () => {
  const lateProof = later<DeliveryProof>()
  let checks = 0
  serve({
    'GET /api/brand/campaigns/42': { ...campaign, status: 'ACTIVE' },
    [`GET ${PROOF}`]: () => (checks++ === 0 ? notSentYet(42).proof : lateProof.promise),
    [`GET ${REVIEWS}`]: () => notSentYet(42).reviews,
    ...campaign43Routes,
  })
  renderPageWithLinkTo43()
  expect(await screen.findByText(/Nour Haddad hasn't sent the proof yet/)).toBeInTheDocument()

  await checkForNewProof()
  fireEvent.click(screen.getByRole('link', { name: 'Open the other campaign' }))
  expect(await screen.findByText(/The creator hasn't sent the proof yet/)).toBeInTheDocument()
  await act(async () => lateProof.resolve(proof({ submissions: [version(1)] })))

  expect(screen.queryByText('Round 1')).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Winter menu launch' })).toBeInTheDocument()
  // The late answer did not make the page reload campaign 42 either.
  expect(requestsTo('GET', '/api/brand/campaigns/42')).toHaveLength(1)
})

it('does not report a decision that finishes after navigating to another campaign', async () => {
  const lateDecision = later<ProofReview>()
  serve({
    'GET /api/brand/campaigns/42': campaign,
    [`GET ${PROOF}`]: proof({ submissions: [version(1)] }),
    [`GET ${REVIEWS}`]: reviews(),
    [`POST ${PROOF}/1/review`]: () => lateDecision.promise,
    ...campaign43Routes,
  })
  renderPageWithLinkTo43()

  fireEvent.click(await screen.findByRole('button', { name: 'Approve proof' }))
  fireEvent.click(
    within(screen.getByRole('group', { name: 'Approve this proof?' })).getByRole('button', {
      name: 'Approve proof',
    }),
  )
  fireEvent.click(screen.getByRole('link', { name: 'Open the other campaign' }))
  expect(await screen.findByText(/The creator hasn't sent the proof yet/)).toBeInTheDocument()
  await act(async () => lateDecision.resolve(review()))

  expect(screen.getByRole('heading', { name: 'Winter menu launch' })).toBeInTheDocument()
  expect(requestsTo('GET', '/api/brand/campaigns/42')).toHaveLength(1)
  expect(requestsTo('GET', PROOF)).toHaveLength(1)
})

it('has no proof section before the campaign is paid', async () => {
  renderPage('AWAITING_PAYMENT')

  expect(await screen.findByRole('heading', { name: 'Awaiting payment' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Delivery proof' })).not.toBeInTheDocument()
  expect(requestsTo('GET', PROOF)).toHaveLength(0)
})
