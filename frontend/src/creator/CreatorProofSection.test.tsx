import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Campaign } from '../api/campaigns'
import { ApiError, apiFetch } from '../api/client'
import type { DeliveryProof, ProofSubmission } from '../api/proof'
import { CreatorCampaignDetailPage } from './CreatorCampaignDetailPage'
import { CreatorProofSection } from './CreatorProofSection'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))

const apiFetchMock = vi.mocked(apiFetch)

const roundOne: ProofSubmission = {
  id: 5,
  round: 1,
  status: 'SUBMITTED',
  createdAt: '2030-01-16T10:00:00Z',
  submittedAt: '2030-01-16T12:05:00Z',
  links: ['https://www.instagram.com/reel/one/'],
  files: [],
}

const shot = (id: number) => ({
  id,
  contentType: 'image/png',
  sizeBytes: 100,
  downloadUrl: `/api/proof-files/${id}`,
})

function proof(overrides: Partial<DeliveryProof> = {}): DeliveryProof {
  return {
    campaignId: 42,
    campaignName: 'Summer menu launch',
    campaignStatus: 'ACTIVE',
    reviewDeadline: null,
    screenshotsRequired: false,
    submissions: [],
    ...overrides,
  }
}

function draft(overrides: Partial<ProofSubmission> = {}): ProofSubmission {
  return {
    id: 6,
    round: 1,
    status: 'DRAFT',
    createdAt: '2030-01-15T10:00:00Z',
    submittedAt: null,
    links: [],
    files: [],
    ...overrides,
  }
}

/** The JSON part of a draft or submit request, as the backend reads it. */
async function proofPart(call: number) {
  const form = apiFetchMock.mock.calls[call][1]?.body as FormData
  const text = await new Promise<string>((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.readAsText(form.get('proof') as Blob)
  })
  return {
    form,
    proof: JSON.parse(text) as { links: string[] | null; retainedFileIds: number[] | null },
  }
}

/** A server answer the test releases when it wants, to check what happens to an answer that arrives late. */
function later<T>() {
  let resolve: (value: T) => void = () => undefined
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

function renderSection(onSubmitted = vi.fn()) {
  render(
    <CreatorProofSection campaignId={42} lastPostingDate="2030-01-30" onSubmitted={onSubmitted} />,
  )
  return onSubmitted
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

afterEach(() => {
  vi.restoreAllMocks()
})

it('saves the links on screen as a draft', async () => {
  apiFetchMock
    .mockResolvedValueOnce(proof())
    .mockResolvedValueOnce(proof({ submissions: [draft({ links: ['https://example.com/post'] })] }))
  renderSection()

  fireEvent.change(await screen.findByLabelText('Link 1'), {
    target: { value: ' https://example.com/post ' },
  })
  expect(screen.getByText(/Unsaved changes/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Save draft' }))

  expect(await screen.findByText(/Draft saved \d/)).toBeInTheDocument()
  expect(apiFetchMock.mock.calls[1][0]).toBe('/api/creator/campaigns/42/proof/draft')
  expect(apiFetchMock.mock.calls[1][1]?.method).toBe('PUT')
  const { proof: sent } = await proofPart(1)
  expect(sent).toEqual({ links: ['https://example.com/post'], retainedFileIds: null })
})

it('uploads a screenshot as soon as it is chosen, together with the links on screen', async () => {
  apiFetchMock
    .mockResolvedValueOnce(proof())
    .mockResolvedValueOnce(proof({ submissions: [draft({ files: [shot(8)] })] }))
  renderSection()

  fireEvent.change(await screen.findByLabelText('Link 1'), {
    target: { value: 'https://example.com/post' },
  })
  const png = new File(['png'], 'insights.png', { type: 'image/png' })
  fireEvent.change(screen.getByLabelText('Add screenshots'), { target: { files: [png] } })

  expect(await screen.findByRole('img', { name: 'Screenshot 1' })).toHaveAttribute(
    'src',
    '/api/proof-files/8',
  )
  const { form, proof: sent } = await proofPart(1)
  expect(form.getAll('files')).toHaveLength(1)
  expect(sent.links).toEqual(['https://example.com/post'])
  // The typed link stays on screen after the upload.
  expect(screen.getByLabelText('Link 1')).toHaveValue('https://example.com/post')
})

it('refuses a file that is not an image, or is larger than 5 MB, without calling the server', async () => {
  apiFetchMock.mockResolvedValueOnce(proof())
  renderSection()
  const input = await screen.findByLabelText('Add screenshots')

  fireEvent.change(input, {
    target: { files: [new File(['%PDF'], 'contract.pdf', { type: 'application/pdf' })] },
  })
  expect(screen.getByRole('alert')).toHaveTextContent('PNG, JPEG, GIF or WebP')

  const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' })
  fireEvent.change(input, { target: { files: [big] } })
  expect(screen.getByRole('alert')).toHaveTextContent('5 MB or smaller')

  expect(apiFetchMock).toHaveBeenCalledTimes(1)
})

it('removing one screenshot keeps the others', async () => {
  apiFetchMock
    .mockResolvedValueOnce(proof({ submissions: [draft({ files: [shot(8), shot(9)] })] }))
    .mockResolvedValueOnce(proof({ submissions: [draft({ files: [shot(9)] })] }))
  renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Remove screenshot 1' }))

  await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(1))
  const { proof: sent } = await proofPart(1)
  expect(sent.retainedFileIds).toEqual([9])
})

it('asks for a working link and a confirmation before sending, then shows the review timer', async () => {
  const sentProof = proof({
    campaignStatus: 'PROOF_SUBMITTED',
    reviewDeadline: '2099-01-21T12:05:00Z',
    submissions: [{ ...roundOne, links: ['https://example.com/post'] }],
  })
  apiFetchMock.mockResolvedValueOnce(proof()).mockResolvedValueOnce(sentProof)
  const onSubmitted = renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('alert')).toHaveTextContent('Add at least one link')

  fireEvent.change(screen.getByLabelText('Link 1'), { target: { value: 'instagram dot com' } })
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('alert')).toHaveTextContent('full web address')

  fireEvent.change(screen.getByLabelText('Link 1'), {
    target: { value: 'https://example.com/post' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('group', { name: 'Send your proof?' })).toBeInTheDocument()
  expect(apiFetchMock).toHaveBeenCalledTimes(1)

  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))

  expect(await screen.findByText(/The brand reviews it by/)).toBeInTheDocument()
  expect(screen.getByRole('progressbar', { name: 'Brand review of proof' })).toBeInTheDocument()
  expect(screen.queryByLabelText('Link 1')).not.toBeInTheDocument()
  expect(onSubmitted).toHaveBeenCalledOnce()
  expect(apiFetchMock.mock.calls[1][0]).toBe('/api/creator/campaigns/42/proof/submit')
  expect(apiFetchMock.mock.calls[1][1]?.method).toBe('POST')
  const { proof: sent } = await proofPart(1)
  expect(sent.links).toEqual(['https://example.com/post'])
})

it('starts a correction round from the last version, and keeps that version in the history', async () => {
  apiFetchMock.mockResolvedValueOnce(
    proof({ campaignStatus: 'CORRECTIONS_REQUESTED', submissions: [roundOne] }),
  )
  renderSection()

  expect(await screen.findByLabelText('Link 1')).toHaveValue('https://www.instagram.com/reel/one/')
  expect(screen.getByText(/Round 2 of 3/)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Proof history' })).toBeInTheDocument()
  expect(screen.getByText('Round 1')).toBeInTheDocument()
})

it("shows the server's reason when a save is refused", async () => {
  apiFetchMock
    .mockResolvedValueOnce(proof())
    .mockRejectedValueOnce(
      new ApiError(400, { detail: 'A proof link cannot exceed 2048 characters.' }),
    )
  renderSection()

  fireEvent.click(await screen.findByRole('button', { name: 'Save draft' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'A proof link cannot exceed 2048 characters.',
  )
})

// EX-F5.1.2
it('refuses an empty link row next to a valid one instead of dropping it, and names the row', async () => {
  apiFetchMock.mockResolvedValueOnce(proof())
  renderSection()

  fireEvent.change(await screen.findByLabelText('Link 1'), {
    target: { value: 'https://example.com/post' },
  })
  fireEvent.click(screen.getByRole('button', { name: /Add link/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))

  expect(screen.getByRole('alert')).toHaveTextContent('Link 2 is empty. Fill it in or remove it.')
  expect(screen.getByLabelText('Link 2')).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByLabelText('Link 1')).not.toHaveAttribute('aria-invalid')
  expect(screen.queryByRole('group', { name: 'Send your proof?' })).not.toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Remove link 2' }))
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('group', { name: 'Send your proof?' })).toBeInTheDocument()
  expect(apiFetchMock).toHaveBeenCalledTimes(1)
})

// FR-F5.1
it('asks for at least one screenshot when the campaign requires it', async () => {
  const linked = draft({ links: ['https://example.com/post'] })
  apiFetchMock
    .mockResolvedValueOnce(proof({ screenshotsRequired: true, submissions: [linked] }))
    .mockResolvedValueOnce(
      proof({ screenshotsRequired: true, submissions: [{ ...linked, files: [shot(8)] }] }),
    )
  renderSection()

  expect(await screen.findByText('(at least one required)')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('alert')).toHaveTextContent(
    'This campaign asks for at least one screenshot.',
  )
  expect(screen.queryByRole('group', { name: 'Send your proof?' })).not.toBeInTheDocument()

  const png = new File(['png'], 'insights.png', { type: 'image/png' })
  fireEvent.change(screen.getByLabelText('Add screenshots'), { target: { files: [png] } })
  expect(await screen.findByRole('img', { name: 'Screenshot 1' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('group', { name: 'Send your proof?' })).toBeInTheDocument()
})

it('keeps screenshots optional when the campaign does not require them', async () => {
  apiFetchMock.mockResolvedValueOnce(
    proof({ submissions: [draft({ links: ['https://example.com/post'] })] }),
  )
  renderSection()

  expect(await screen.findByText('(optional)')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  expect(screen.getByRole('group', { name: 'Send your proof?' })).toBeInTheDocument()
})

it('starts from a clean form when another campaign is opened, and ignores a late save of the first', async () => {
  const lateSave = later<DeliveryProof>()
  apiFetchMock.mockImplementation(async (path: string) => {
    if (path === '/api/creator/campaigns/42/proof') return proof()
    if (path === '/api/creator/campaigns/42/proof/draft') return lateSave.promise
    if (path === '/api/creator/campaigns/43/proof') return proof({ campaignId: 43 })
    throw new Error(`Unexpected call to ${path}`)
  })
  const props = { lastPostingDate: '2030-01-30', onSubmitted: vi.fn() }
  const { rerender } = render(<CreatorProofSection campaignId={42} {...props} />)

  fireEvent.change(await screen.findByLabelText('Link 1'), {
    target: { value: 'https://example.com/campaign-42' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Save draft' }))
  rerender(<CreatorProofSection campaignId={43} {...props} />)

  expect(await screen.findByLabelText('Link 1')).toHaveValue('')
  expect(screen.queryByText(/Unsaved changes/)).not.toBeInTheDocument()
  await act(async () =>
    lateSave.resolve(
      proof({ submissions: [draft({ links: ['https://example.com/campaign-42'] })] }),
    ),
  )
  expect(screen.getByLabelText('Link 1')).toHaveValue('')
  expect(screen.queryByText(/Draft saved/)).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Save draft' })).toBeEnabled()
})

it('does not tell the page about a send that finishes after another campaign was opened', async () => {
  const lateSend = later<DeliveryProof>()
  apiFetchMock.mockImplementation(async (path: string) => {
    if (path === '/api/creator/campaigns/42/proof') {
      return proof({ submissions: [draft({ links: ['https://example.com/post'] })] })
    }
    if (path === '/api/creator/campaigns/42/proof/submit') return lateSend.promise
    if (path === '/api/creator/campaigns/43/proof') return proof({ campaignId: 43 })
    throw new Error(`Unexpected call to ${path}`)
  })
  const props = { lastPostingDate: '2030-01-30', onSubmitted: vi.fn() }
  const { rerender } = render(<CreatorProofSection campaignId={42} {...props} />)

  fireEvent.click(await screen.findByRole('button', { name: 'Send proof' }))
  fireEvent.click(screen.getByRole('button', { name: 'Send proof' }))
  rerender(<CreatorProofSection campaignId={43} {...props} />)
  expect(await screen.findByLabelText('Link 1')).toHaveValue('')
  await act(async () =>
    lateSend.resolve(
      proof({ campaignStatus: 'PROOF_SUBMITTED', submissions: [{ ...roundOne, id: 6 }] }),
    ),
  )

  expect(props.onSubmitted).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Send proof' })).toBeEnabled()
  expect(screen.queryByText(/The brand reviews it by/)).not.toBeInTheDocument()
})

const campaign: Campaign = {
  id: 42,
  name: 'Summer menu launch',
  status: 'ACTIVE',
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
    path.endsWith('/proof') ? proof() : { ...campaign, status },
  )
  render(
    <MemoryRouter initialEntries={['/creator/campaigns/42']}>
      <Routes>
        <Route path="/creator/campaigns/:id" element={<CreatorCampaignDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

it('shows the proof form on the campaign page once the campaign is active', async () => {
  renderPage('ACTIVE')

  expect(await screen.findByRole('heading', { name: 'Delivery proof' })).toBeInTheDocument()
  expect(apiFetchMock).toHaveBeenCalledWith('/api/creator/campaigns/42/proof')
})

it('does not ask for proof before the campaign is paid', async () => {
  renderPage('AWAITING_PAYMENT')

  expect(await screen.findByText(/Waiting for the brand to pay/)).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Delivery proof' })).not.toBeInTheDocument()
  expect(apiFetchMock).not.toHaveBeenCalledWith('/api/creator/campaigns/42/proof')
})
