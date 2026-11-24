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
  const [rating, setRating] = useState(0)
  const [hoveredRating, setHoveredRating] = useState(0)
  const [text, setText] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [alreadyReviewed, setAlreadyReviewed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    setLoading(true)
    setReview(null)
    setRating(0)
    setHoveredRating(0)
    setText('')
    setAlreadyReviewed(false)
    setError(null)

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

    if (rating === 0) {
      setError('Please select a rating.')
      return
    }

    setSubmitting(true)
    setError(null)

    try {
      const saved = await submitCampaignReview(campaignId, {
        rating,
        text: text.trim() || null,
      })
      setReview(saved)
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.status === 409 &&
        err.message.includes('Campaign already reviewed')
      ) {
        setAlreadyReviewed(true)
        setError('This campaign has already been reviewed.')
      } else {
        setError(
          err instanceof ApiError
            ? (err.problem.errors?.rating ?? err.problem.errors?.text ?? err.message)
            : 'Could not submit the review.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p>Loading review...</p>

  return (
    <section className="fl-card fl-section fl-stack">
      <h2>Review creator</h2>

      {error && (
        <div className="fl-alert fl-alert--danger" role="alert">
          {error}
        </div>
      )}

      {review ? (
        <div>
          <p>Rating: {review.rating} / 5 stars</p>
          {review.text && <p>{review.text}</p>}
          <p>This review has been submitted and cannot be edited.</p>
        </div>
      ) : alreadyReviewed ? (
        <p>This campaign cannot be reviewed again.</p>
      ) : (
        <form onSubmit={(event) => void submit(event)} className="fl-stack">
          <div>
            <p id="creator-rating-label">Rating</p>
            <div
              role="group"
              aria-labelledby="creator-rating-label"
              onMouseLeave={() => setHoveredRating(0)}
              style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
            >
              {[1, 2, 3, 4, 5].map((stars) => (
                <button
                  key={stars}
                  type="button"
                  aria-label={`Rate ${stars} ${stars === 1 ? 'star' : 'stars'}`}
                  aria-pressed={rating === stars}
                  onClick={() => setRating(stars)}
                  onMouseEnter={() => setHoveredRating(stars)}
                  onFocus={() => setHoveredRating(stars)}
                  onBlur={() => setHoveredRating(0)}
                  disabled={submitting}
                  style={{
                    background: 'none',
                    border: 'none',
                    padding: '4px',
                    cursor: submitting ? 'default' : 'pointer',
                    fontSize: '32px',
                    lineHeight: 1,
                    color: stars <= (hoveredRating || rating) ? '#e5ab32' : '#aaa',
                  }}
                >
                  ★
                </button>
              ))}
              <span aria-live="polite" style={{ marginLeft: '8px' }}>
                {rating > 0 ? `${rating}/5` : 'Select a rating'}
              </span>
            </div>
          </div>

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
            disabled={submitting || rating === 0}
          >
            {submitting ? 'Submitting...' : 'Submit review'}
          </button>
        </form>
      )}
    </section>
  )
}
