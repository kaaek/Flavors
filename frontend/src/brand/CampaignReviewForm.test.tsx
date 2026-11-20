import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { getCampaignReview, submitCampaignReview } from '../api/campaignReviews'
import { ApiError } from '../api/client'
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

it('shows five stars without a default rating', async () => {
  getReviewMock.mockResolvedValue(null)

  render(<CampaignReviewForm campaignId={15} />)

  const button = await screen.findByRole('button', {
    name: 'Submit review',
  })

  expect(button).toBeDisabled()
  expect(screen.getAllByRole('button', { name: /^Rate \d stars?$/ })).toHaveLength(5)
  expect(screen.getByText('Select a rating')).toBeInTheDocument()
  expect(submitReviewMock).not.toHaveBeenCalled()
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

  expect(screen.queryByRole('button', { name: 'Submit review' })).not.toBeInTheDocument()

  expect(submitReviewMock).not.toHaveBeenCalled()
})

it('keeps the form after a load error', async () => {
  getReviewMock.mockRejectedValue(new Error('Network failure'))

  render(<CampaignReviewForm campaignId={15} />)

  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the review.')

  expect(screen.getByRole('button', { name: 'Submit review' })).toBeInTheDocument()
})

it('keeps the form after a validation error', async () => {
  const user = userEvent.setup()
  getReviewMock.mockResolvedValue(null)
  submitReviewMock.mockRejectedValue(
    new ApiError(400, {
      detail: 'Invalid rating',
      errors: { rating: 'Rating must be between 1 and 5' },
    }),
  )

  render(<CampaignReviewForm campaignId={15} />)

  await user.click(await screen.findByRole('button', { name: 'Rate 4 stars' }))

  await user.click(screen.getByRole('button', { name: 'Submit review' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Rating must be between 1 and 5')

  expect(screen.getByRole('button', { name: 'Submit review' })).toBeInTheDocument()
})

it('hides the form when the campaign was already reviewed', async () => {
  const user = userEvent.setup()
  getReviewMock.mockResolvedValue(null)
  submitReviewMock.mockRejectedValue(
    new ApiError(409, {
      detail: 'Campaign already reviewed',
    }),
  )

  render(<CampaignReviewForm campaignId={15} />)

  await user.click(await screen.findByRole('button', { name: 'Rate 5 stars' }))

  await user.click(screen.getByRole('button', { name: 'Submit review' }))

  expect(await screen.findByText('This campaign cannot be reviewed again.')).toBeInTheDocument()

  expect(screen.queryByRole('button', { name: 'Submit review' })).not.toBeInTheDocument()
})

it('keeps the form after other conflicts', async () => {
  const user = userEvent.setup()
  getReviewMock.mockResolvedValue(null)
  submitReviewMock.mockRejectedValue(
    new ApiError(409, {
      detail: 'Campaign is not completed',
    }),
  )

  render(<CampaignReviewForm campaignId={15} />)

  await user.click(await screen.findByRole('button', { name: 'Rate 4 stars' }))

  await user.click(screen.getByRole('button', { name: 'Submit review' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Campaign is not completed')

  expect(screen.getByRole('button', { name: 'Submit review' })).toBeInTheDocument()
})

it('selects the rating by clicking a star', async () => {
  const user = userEvent.setup()
  getReviewMock.mockResolvedValue(null)

  render(<CampaignReviewForm campaignId={15} />)

  await user.click(await screen.findByRole('button', { name: 'Rate 3 stars' }))

  expect(screen.getByText('3/5')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Rate 3 stars' })).toHaveAttribute(
    'aria-pressed',
    'true',
  )

  expect(screen.getByRole('button', { name: 'Submit review' })).toBeEnabled()
})
