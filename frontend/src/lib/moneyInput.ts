/**
 * Reads what a person typed in a dollar field as integer cents: "25" is 2500, "25.5" is 2550,
 * "$25.50" is 2550. Returns null when it is not an amount. It works on the text, not with
 * `* 100`, so floating-point errors (19.99 * 100) can never creep in.
 */
export function parseUSDToCents(input: string): number | null {
  const text = input.trim().replace(/^\$\s*/, '')
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(text)) return null
  const [whole, fraction = ''] = text.split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}

/** Integer cents as a plain dollar amount for an input field: 10050 is "100.50". */
export function centsToDollars(cents: number): string {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`
}
