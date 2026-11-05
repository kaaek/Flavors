/** The icons of design/icons/icons.svg (copied to /icons.svg by design/sync-to-frontend.sh). */
export type IconName =
  | 'search'
  | 'check'
  | 'check-circle'
  | 'x'
  | 'x-circle'
  | 'alert'
  | 'info'
  | 'clock'
  | 'qr'
  | 'dollar'
  | 'shield'
  | 'lock'
  | 'upload'
  | 'bell'
  | 'user'
  | 'users'
  | 'chart'
  | 'tag'
  | 'pin'
  | 'phone'
  | 'plus'
  | 'camera'
  | 'arrow-right'
  | 'star'

/** Decorative: the words next to an icon carry the meaning, so it is hidden from screen readers. */
export function Icon({ name }: { name: IconName }) {
  return (
    <svg className="fl-icon" aria-hidden="true">
      <use href={`/icons.svg#i-${name}`} />
    </svg>
  )
}
