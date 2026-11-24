/**
 * "2 days 3 hours", the time until the deadline, from the deadline and the current time in ms. Returns null once the
 * deadline has passed. Pure, so it can be tested without a clock.
 */
export function durationLeft(deadlineIso: string, nowMs: number): string | null {
  const remainingMs = Date.parse(deadlineIso) - nowMs
  if (remainingMs <= 0) return null
  const minutes = Math.ceil(remainingMs / 60_000)

  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const mins = minutes % 60
  const parts: string[] = []
  if (days > 0) parts.push(`${days} ${days === 1 ? 'day' : 'days'}`)
  if (hours > 0) parts.push(`${hours} ${hours === 1 ? 'hour' : 'hours'}`)
  if (days === 0 && mins > 0) parts.push(`${mins} ${mins === 1 ? 'minute' : 'minutes'}`)
  return parts.join(' ')
}

/**
 * "2 days 3 hours left to pay", from the deadline and the current time in ms. Pure, so it can be tested without a clock.
 * Returns null when there is no deadline.
 */
export function timeLeftText(deadlineIso: string | null, nowMs: number): string | null {
  if (!deadlineIso) return null
  const left = durationLeft(deadlineIso, nowMs)
  return left === null ? 'The time to pay has passed.' : `${left} left to pay`
}
