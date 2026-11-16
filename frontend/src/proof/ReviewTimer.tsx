import { useState } from 'react'
import { durationLeft } from '../lib/timeLeft'

/** Timer C lasts five days from the latest final submission (FR-F6.5). */
const REVIEW_MS = 5 * 24 * 60 * 60 * 1000

/** The design's Timer C bar: how much of the brand's five-day review has passed. */
export function ReviewTimer({ deadline, label }: { deadline: string; label: string }) {
  // Read once when the timer appears; a five-day countdown doesn't need to tick while the page is open.
  const [now] = useState(() => Date.now())
  const left = durationLeft(deadline, now)
  const started = Date.parse(deadline) - REVIEW_MS
  const used = Math.min(100, Math.max(0, Math.round(((now - started) / REVIEW_MS) * 100)))

  return (
    <div className="fl-timer">
      <div className="fl-timer__row">
        <span>{label}</span>
        <span>{left === null ? 'Review time has ended' : `${left} left`}</span>
      </div>
      <div
        className="fl-progress"
        role="progressbar"
        aria-label={label}
        aria-valuenow={used}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <i style={{ width: `${used}%` }} />
      </div>
    </div>
  )
}
