import { useCallback, useEffect, useRef, useState } from 'react'
import type { CampaignStatus } from '../api/campaigns'
import {
  PROOF_REFRESH_MS,
  getBrandProof,
  submittedVersions,
  type DeliveryProof,
} from '../api/proof'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { ProofRounds } from '../proof/ProofRounds'
import { formatDay } from '../proof/dates'

/** The creator can still send a version, so the brand's view keeps checking for one. */
const awaitingVersion = (status: CampaignStatus) =>
  status === 'ACTIVE' || status === 'CORRECTIONS_REQUESTED'

interface Props {
  campaignId: number
  creatorName: string | null
  lastPostingDate: string | null
  /** A new version changes the campaign's status, so the page reloads its header. */
  onChanged: () => void
}

/**
 * The creator's sent proof, read-only (UC-F5.1). The brand never sees a draft: the backend leaves drafts out of
 * this response.
 *
 * Keyed by the campaign, so opening another campaign starts from its own proof: nothing loaded for one campaign,
 * including a late answer, can show on another.
 */
export function BrandProofSection(props: Props) {
  return <CampaignProof key={props.campaignId} {...props} />
}

function CampaignProof({ campaignId, creatorName, lastPostingDate, onChanged }: Props) {
  const load = useCallback(() => getBrandProof(campaignId), [campaignId])
  const result = useFetchWith<DeliveryProof>(load)
  // The proof as the last check found it, like the page's campaignOverride.
  const [refreshed, setRefreshed] = useState<DeliveryProof | null>(null)
  const proof = refreshed ?? (result.status === 'ready' ? result.data : null)
  const status = proof?.campaignStatus ?? null
  const waiting = status !== null && awaitingVersion(status)
  const who = creatorName ?? 'The creator'
  const whoInSentence = creatorName ?? 'the creator'

  // The latest callback, without restarting the timer each time the page renders.
  const changed = useRef(onChanged)
  useEffect(() => {
    changed.current = onChanged
  }, [onChanged])

  // FR-F5.5: a version the creator sends shows up here within a minute, without a reload.
  useEffect(() => {
    if (!waiting) return
    let stopped = false
    const timer = window.setInterval(() => {
      load()
        .then((fresh) => {
          if (stopped) return
          setRefreshed(fresh)
          if (fresh.campaignStatus !== status) changed.current()
        })
        .catch(() => undefined)
    }, PROOF_REFRESH_MS)
    return () => {
      stopped = true
      window.clearInterval(timer)
    }
  }, [waiting, status, load])

  if (proof === null) {
    if (result.status === 'error') {
      return (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load the delivery proof. Please reload the page.</div>
        </div>
      )
    }
    return (
      <div className="fl-card" aria-busy="true" aria-label="Loading delivery proof">
        <div className="fl-skel fl-skel--line" />
      </div>
    )
  }

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
        <p className="fl-muted">Waiting for a new version from {whoInSentence}.</p>
      )}
      {versions.length > 0 && (
        <ProofRounds versions={versions} openLatest={!waitingForNewVersion} />
      )}
      {waiting && (
        <p className="fl-hint">
          This section updates by itself when {whoInSentence} sends a version.
        </p>
      )}
    </section>
  )
}
