import { describe, expect, it } from 'vitest'
import { centsToDollars, parseUSDToCents } from './moneyInput'

describe('parseUSDToCents', () => {
  it('reads whole dollars and cents', () => {
    expect(parseUSDToCents('25')).toBe(2500)
    expect(parseUSDToCents('25.5')).toBe(2550)
    expect(parseUSDToCents('25.05')).toBe(2505)
    expect(parseUSDToCents('0')).toBe(0)
  })

  it('ignores spaces and a leading dollar sign', () => {
    expect(parseUSDToCents(' 7 ')).toBe(700)
    expect(parseUSDToCents('$25.50')).toBe(2550)
    expect(parseUSDToCents('$ 25')).toBe(2500)
  })

  it('is exact where floating point is not', () => {
    expect(parseUSDToCents('19.99')).toBe(1999)
    expect(parseUSDToCents('1.15')).toBe(115)
  })

  it('refuses anything that is not an amount', () => {
    for (const bad of ['', '  ', 'abc', '-5', '1.234', '1,000', '25.', '.5', '1e3']) {
      expect(parseUSDToCents(bad), bad).toBeNull()
    }
  })
})

describe('centsToDollars', () => {
  it('writes cents as dollars with two decimals', () => {
    expect(centsToDollars(10050)).toBe('100.50')
    expect(centsToDollars(2500)).toBe('25.00')
    expect(centsToDollars(5)).toBe('0.05')
    expect(centsToDollars(0)).toBe('0.00')
  })

  it('round-trips with parseUSDToCents', () => {
    for (const cents of [0, 5, 99, 100, 1999, 10050]) {
      expect(parseUSDToCents(centsToDollars(cents))).toBe(cents)
    }
  })
})
