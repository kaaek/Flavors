import { statusLabel, type CampaignStatus } from '../api/campaigns'

const tone: Record<CampaignStatus, 'neutral' | 'warning' | 'info' | 'success' | 'danger'> = {
  DRAFT: 'neutral',
  AWAITING_CREATOR_RESPONSE: 'warning',
  COUNTERED: 'warning',
  AWAITING_PAYMENT: 'warning',
  PROOF_SUBMITTED: 'info',
  CORRECTIONS_REQUESTED: 'warning',
  ACTIVE: 'info',
  COMPLETED: 'success',
  REJECTED: 'danger',
  DECLINED: 'danger',
  EXPIRED: 'danger',
  DISPUTED: 'danger',
  CANCELLED: 'danger',
  REMOVED: 'danger',
}

/** A campaign's status as a chip; the words carry the meaning and the design's symbol backs up the colour. */
export function CampaignStatusChip({ status }: { status: CampaignStatus }) {
  return <span className={`fl-chip fl-chip--${tone[status]}`}>{statusLabel[status]}</span>
}
