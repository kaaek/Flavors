import { useCallback, useEffect, useRef, useState } from 'react'
import type { CampaignStatus } from '../api/campaigns'
import { PROOF_REFRESH_MS, getBrandProof, submittedVersions } from '../api/proof'
import { getBrandProofReviews } from '../api/proofReviews'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { ProofOutcome } from '../proof/ProofOutcome'
import { ProofRounds } from '../proof/ProofRounds'
import { formatDay } from '../proof/dates'
import { ProofReviewPanel } from './ProofReviewPanel'

/** The creator can still send a version, so the brand's view keeps checking for one. */
const awaitingVersion = (status: CampaignStatus) =>
  status === 'ACTIVE' || status === 'CORRECTIONS_REQUESTED'

interface Props {
  campaignId: number
  creatorName: string | null
  lastPostingDate: string | null
  /** A new version or a decision changes the campaign's status, so the page reloads its header. */
  onChanged: () => void
}

/**
 * The creator's sent proof, read-only (UC-F5.1), and the brand's review of it (UC-F6.1). The brand never sees a draft:
 * the backend leaves drafts out of this response.
 *
 * Keyed by the campaign, so opening another campaign starts from its own proof and reviews: nothing loaded for one
 * campaign, including a late answer, can show on another.
 */
export function BrandProofSection(props: Props) {
  return <CampaignProof key={props.campaignId} {...props} />
}

function CampaignProof({ campaignId, creatorName, lastPostingDate, onChanged }: Props) {
  const load = useCallback(
    () => Promise.all([getBrandProof(campaignId), getBrandProofReviews(campaignId)]),
    [campaignId],
  )
  const result = useFetchWith(load)
  // The proof and reviews as the last reload found them, like the page's campaignOverride.
  const [refreshed, setRefreshed] = useState<Awaited<ReturnType<typeof load>> | null>(null)
  const data = refreshed ?? (result.status === 'ready' ? result.data : null)
  const status = data ? data[0].campaignStatus : null
  const waiting = status !== null && awaitingVersion(status)
  const who = creatorName ?? 'The creator'
  const whoInSentence = creatorName ?? 'the creator'

  // Every reload (the 30-second check, or after a decision) is numbered and only the newest answer is shown, so an
  // older answer that arrives late can't replace a newer one. Nothing is shown once the section is gone.
  const requests = useRef(0)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  const reload = useCallback(async () => {
    const request = ++requests.current
    const fresh = await load()
    if (!mounted.current || request !== requests.current) return null
    setRefreshed(fresh)
    return fresh
  }, [load])

  // The latest callback, without restarting the timer each time the page renders.
  const changed = useRef(onChanged)
  useEffect(() => {
    changed.current = onChanged
  }, [onChanged])

  // FR-F5.5: a version the creator sends shows up here within a minute, without a reload.
  useEffect(() => {
    if (!waiting) return
    const timer = window.setInterval(() => {
      reload()
        .then((fresh) => {
          if (fresh && fresh[0].campaignStatus !== status) changed.current()
        })
        .catch(() => undefined)
    }, PROOF_REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [waiting, status, reload])

  if (data === null) {
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

  const [proof, reviews] = data
  const versions = submittedVersions(proof)
  const waitingForNewVersion = proof.campaignStatus === 'CORRECTIONS_REQUESTED'

  return (
    <>
      <ProofOutcome
        reviews={reviews.reviews}
        latestVersionId={versions[0]?.id ?? null}
        campaignStatus={proof.campaignStatus}
        correctionRoundsUsed={reviews.correctionRoundsUsed}
        viewer="brand"
        creatorName={creatorName}
      />
      {/* Keyed by the version under review: a new version gets fresh review controls. */}
      <ProofReviewPanel
        key={reviews.latestSubmissionId ?? 'none'}
        campaignId={campaignId}
        reviews={reviews}
        creatorName={creatorName}
        onDecided={() => {
          // The decision is saved; if this reload fails, retrying the same decision returns the saved one.
          void reload().catch(() => undefined)
          onChanged()
        }}
      />
      <section className="fl-card fl-section fl-stack" aria-labelledby="brand-proof-title">
        <h2 id="brand-proof-title">Delivery proof</h2>
        {versions.length === 0 && (
          <p className="fl-muted">
            {who} hasn't sent the proof yet. The last posting date is {formatDay(lastPostingDate)}.
          </p>
        )}
        {versions.length > 0 && (
          <ProofRounds
            versions={versions}
            openLatest={!waitingForNewVersion}
            reviews={reviews.reviews}
          />
        )}
        {waiting && (
          <p className="fl-hint">
            This section updates by itself when {whoInSentence} sends a version.
          </p>
        )}
      </section>
    </>
  )
}
