import { CURRENCY, LOCALE } from './locale'

const usd = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY })

/**
 * Formats integer cents for display: formatUSD(123450) is "$1,234.50".
 * The API only ever carries cents, so amounts are never floating-point dollars until this point.
 */
export function formatUSD(cents: number): string {
  return usd.format(cents / 100)
}
