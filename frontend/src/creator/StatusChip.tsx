export type VerificationStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'

const chips: Record<VerificationStatus, { tone: string; label: string }> = {
  PENDING_APPROVAL: { tone: 'warning', label: 'Pending approval' },
  APPROVED: { tone: 'success', label: 'Verified' },
  REJECTED: { tone: 'danger', label: 'Not approved' },
}

/** The creator's approval state as a chip (the design adds the symbol, so it never relies on colour alone). */
export function StatusChip({ status }: { status: VerificationStatus }) {
  const { tone, label } = chips[status]
  return <span className={`fl-chip fl-chip--${tone}`}>{label}</span>
}
