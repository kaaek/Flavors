import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { REVIEW_TIMER_TICK_MS, ReviewTimer } from './ReviewTimer'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2030-01-20T10:00:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

it('counts down while the page stays open, and says when the review time has ended', () => {
  // 1 hour and 1 minute before the deadline.
  render(<ReviewTimer deadline="2030-01-20T11:01:00Z" label="Brand review of proof" />)
  expect(screen.getByText('1 hour 1 minute left')).toBeInTheDocument()

  act(() => vi.advanceTimersByTime(REVIEW_TIMER_TICK_MS))
  expect(screen.getByText('1 hour left')).toBeInTheDocument()

  act(() => vi.advanceTimersByTime(30 * REVIEW_TIMER_TICK_MS))
  expect(screen.getByText('30 minutes left')).toBeInTheDocument()

  act(() => vi.advanceTimersByTime(30 * REVIEW_TIMER_TICK_MS))
  expect(screen.getByText('Review time has ended')).toBeInTheDocument()
  expect(screen.getByRole('progressbar', { name: 'Brand review of proof' })).toHaveAttribute(
    'aria-valuenow',
    '100',
  )
  // Nothing is left to count, so the timer stops.
  expect(vi.getTimerCount()).toBe(0)
})
