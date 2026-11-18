
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import {
  getCampaignReview,
  submitCampaignReview,
} from '../api/campaignReviews'
import { CampaignReviewForm } from './CampaignReviewForm'

vi.mock('../api/campaignReviews', () => ({
  getCampaignReview: vi.fn(),
  submitCampaignReview: vi.fn(),
}))

const getReviewMock = vi.mocked(getCampaignReview)
const submitReviewMock = vi.mocked(submitCampaignReview)

beforeEach(() => {
  getReviewMock.mockReset()
  submitReviewMock.mockReset()
})

it('shows the form when no review exists', async () => {
  getReviewMock.mockResolvedValue(null)

  render(<CampaignReviewForm campaignId={15} />)

  expect(
    await screen.findByRole('button', { name: 'Submit review' }),
  ).toBeInTheDocument()
})

it('submits a rating and review text', async () => {
  const user = userEvent.setup()
  getReviewMock.mockResolvedValue(null)

  submitReviewMock.mockResolvedValue({
    id: 1,
    campaignId: 15,
    rating: 4,
    text: 'Great collaboration!',
    createdAt: '2026-11-18T12:00:00Z',
  })

  render(<CampaignReviewForm campaignId={15} />)

  await user.selectOptions(
    await screen.findByLabelText('Rating'),
    '4',
  )

  await user.type(
    screen.getByLabelText('Review (optional)'),
    'Great collaboration!',
  )

  await user.click(
    screen.getByRole('button', { name: 'Submit review' }),
  )

  expect(submitReviewMock).toHaveBeenCalledWith(15, {
    rating: 4,
    text: 'Great collaboration!',
  })

  expect(
    await screen.findByText(
      'This review has been submitted and cannot be edited.',
    ),
  ).toBeInTheDocument()
})

it('does not allow another review after submission', async () => {
  getReviewMock.mockResolvedValue({
    id: 1,
    campaignId: 15,
    rating: 5,
    text: 'Excellent!',
    createdAt: '2026-11-18T12:00:00Z',
  })

  render(<CampaignReviewForm campaignId={15} />)

  expect(await screen.findByText('Excellent!')).toBeInTheDocument()

  expect(
    screen.queryByRole('button', { name: 'Submit review' }),
  ).not.toBeInTheDocument()

  expect(submitReviewMock).not.toHaveBeenCalled()
})
