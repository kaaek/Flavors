import { useCallback } from 'react'
import { getBrandProof, submittedVersions, type DeliveryProof } from '../api/proof'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { ProofRounds } from '../proof/ProofRounds'
import { formatDay } from '../proof/dates'

/**
 * The creator's sent proof, read-only (UC-F5.1). The brand never sees a draft: the backend leaves drafts out of
 * this response.
 */
export function BrandProofSection({
  campaignId,
  creatorName,
  lastPostingDate,
}: {
  campaignId: number
  creatorName: string | null
  lastPostingDate: string | null
}) {
  const load = useCallback(() => getBrandProof(campaignId), [campaignId])
  const result = useFetchWith<DeliveryProof>(load)
  const who = creatorName ?? 'The creator'

  if (result.status === 'loading') {
    return (
      <div className="fl-card" aria-busy="true" aria-label="Loading delivery proof">
        <div className="fl-skel fl-skel--line" />
      </div>
    )
  }
  if (result.status === 'error') {
    return (
      <div className="fl-alert fl-alert--danger" role="alert">
        <Icon name="alert" />
        <div>Could not load the delivery proof. Please reload the page.</div>
      </div>
    )
  }

  const proof = result.data
  const versions = submittedVersions(proof)
  const waitingForNewVersion = proof.campaignStatus === 'CORRECTIONS_REQUESTED'

  return (
    <section className="fl-card fl-section fl-stack" aria-labelledby="brand-proof-title">
      <h2 id="brand-proof-title">Delivery proof</h2>
      {versions.length === 0 && (
        <p className="fl-muted">
          {who} hasn't sent the proof yet. The last posting date is {formatDay(lastPostingDate)}.
        </p>
      )}
      {versions.length > 0 && waitingForNewVersion && (
        <p className="fl-muted">Waiting for a new version from {who}.</p>
      )}
      {versions.length > 0 && (
        <ProofRounds versions={versions} openLatest={!waitingForNewVersion} />
      )}
    </section>
  )
}
