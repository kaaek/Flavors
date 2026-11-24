import { expect, it } from 'vitest'
import { durationLeft, timeLeftText } from './timeLeft'

const now = Date.parse('2030-01-03T10:00:00Z')

it('counts whole days and hours while more than a day is left (TC-F7.1.5)', () => {
  expect(timeLeftText('2030-01-05T13:00:00Z', now)).toBe('2 days 3 hours left to pay')
})

it('shows a single day without the plural', () => {
  expect(timeLeftText('2030-01-04T10:00:00Z', now)).toBe('1 day left to pay')
})

it('shows hours and minutes inside the last day', () => {
  expect(timeLeftText('2030-01-03T12:30:00Z', now)).toBe('2 hours 30 minutes left to pay')
})

it('says the time has passed once the deadline is reached', () => {
  expect(timeLeftText('2030-01-03T09:00:00Z', now)).toBe('The time to pay has passed.')
})

it('still counts the last minute before the deadline as time left', () => {
  expect(timeLeftText('2030-01-03T10:00:30Z', now)).toBe('1 minute left to pay')
})

it('has no text without a deadline', () => {
  expect(timeLeftText(null, now)).toBeNull()
})

it('gives the bare duration for other timers, and null once the deadline has passed', () => {
  expect(durationLeft('2030-01-08T10:00:00Z', now)).toBe('5 days')
  expect(durationLeft('2030-01-03T10:00:00Z', now)).toBeNull()
})
