
import { useEffect, useState } from 'react'
import {
  getCampaignReview,
  submitCampaignReview,
  type CampaignReviewResponse,
} from '../api/campaignReviews'
import { ApiError } from '../api/client'

interface Props {
  campaignId: number
}

export function CampaignReviewForm({ campaignId }: Props) {
  const [review, setReview] = useState<CampaignReviewResponse | null>(null)
  const [rating, setRating] = useState(5)
  const [text, setText] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    getCampaignReview(campaignId)
      .then((data) => {
        if (!cancelled) setReview(data)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load the review.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [campaignId])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      const saved = await submitCampaignReview(campaignId, {
        rating,
        text: text.trim() || null,
      })
      setReview(saved)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Could not submit the review.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p>Loading review...</p>

  return (
    <section className="fl-card fl-section fl-stack">
      <h2>Review creator</h2>

      {error && <div className="fl-alert fl-alert--danger" role="alert">{error}</div>}

      {review ? (
        <div>
          <p>Rating: {review.rating} / 5 stars</p>
          {review.text && <p>{review.text}</p>}
          <p>This review has been submitted and cannot be edited.</p>
        </div>
      ) : !error ? (
        <form onSubmit={(event) => void submit(event)} className="fl-stack">
          <label htmlFor="creator-rating">Rating</label>
          <select
            id="creator-rating"
            value={rating}
            onChange={(event) => setRating(Number(event.target.value))}
          >
            {[1, 2, 3, 4, 5].map((stars) => (
              <option key={stars} value={stars}>
                {stars} {stars === 1 ? 'star' : 'stars'}
              </option>
            ))}
          </select>

          <label htmlFor="creator-review-text">Review (optional)</label>
          <textarea
            id="creator-review-text"
            value={text}
            onChange={(event) => setText(event.target.value)}
            maxLength={1000}
            rows={4}
          />

          <button
            type="submit"
            className="fl-btn fl-btn--primary"
            disabled={submitting}
          >
            {submitting ? 'Submitting...' : 'Submit review'}
          </button>
        </form>
      ) : null}
    </section>
  )
}
