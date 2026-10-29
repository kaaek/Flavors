import { describe, expect, it } from 'vitest'
import { formatUSD } from './money'

describe('formatUSD', () => {
  it('formats cents as dollars with grouping and two decimals', () => {
    expect(formatUSD(123450)).toBe('$1,234.50')
    expect(formatUSD(5)).toBe('$0.05')
    expect(formatUSD(0)).toBe('$0.00')
  })

  it('formats negative amounts', () => {
    expect(formatUSD(-250)).toBe('-$2.50')
  })
})
