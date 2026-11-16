import { useEffect, useState } from 'react'
import { durationLeft } from '../lib/timeLeft'

/** Timer C lasts five days from the latest final submission (FR-F6.5). */
const REVIEW_MS = 5 * 24 * 60 * 60 * 1000

/** The text shows minutes in the last day, so the timer is redrawn every minute while the review is running. */
export const REVIEW_TIMER_TICK_MS = 60_000

/** The design's Timer C bar: how much of the brand's five-day review has passed. */
export function ReviewTimer({ deadline, label }: { deadline: string; label: string }) {
  const [now, setNow] = useState(() => Date.now())
  const ended = Date.parse(deadline) <= now
  useEffect(() => {
    if (ended) return
    const timer = window.setInterval(() => setNow(Date.now()), REVIEW_TIMER_TICK_MS)
    return () => window.clearInterval(timer)
  }, [ended])
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
