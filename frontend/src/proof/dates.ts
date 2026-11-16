import { LOCALE } from '../lib/locale'

const when = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })
// Campaign dates are calendar days without a time zone, so they are formatted in UTC to keep the same day.
const day = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeZone: 'UTC' })

export const formatWhen = (iso: string | null) => (iso ? when.format(new Date(iso)) : '—')

export const formatDay = (isoDate: string | null) =>
  isoDate ? day.format(new Date(`${isoDate}T00:00:00Z`)) : '—'
