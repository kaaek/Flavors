import type { ProofSubmission } from '../api/proof'
import type { ProofDecision, ProofReview } from '../api/proofReviews'
import { formatWhen } from './dates'
import './proof.css'

const decisionChip: Record<ProofDecision, { label: string; tone: string }> = {
  APPROVED: { label: 'Approved', tone: 'success' },
  CORRECTIONS_REQUESTED: { label: 'Corrections requested', tone: 'warning' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
}

/** The brand's decision on one version (EEC-47), with the words carrying the meaning, not only the colour. */
export function DecisionChip({ decision }: { decision: ProofDecision }) {
  const chip = decisionChip[decision]
  return <span className={`fl-chip fl-chip--${chip.tone}`}>{chip.label}</span>
}

/** Only web links become clickable. The backend already refuses anything else in a sent version; this is a backstop. */
const isWebLink = (value: string) => /^https?:\/\//i.test(value)

/** The links and screenshots of one proof version. Screenshots open full size through the authenticated proxy. */
export function ProofVersion({ submission }: { submission: ProofSubmission }) {
  return (
    <>
      {submission.links.length > 0 && (
        <ul className="fl-prooflinks" aria-label="Links">
          {submission.links.map((link, index) => (
            <li key={index}>
              {isWebLink(link) ? (
                <a className="fl-link" href={link} target="_blank" rel="noopener noreferrer">
                  {link}
                </a>
              ) : (
                link
              )}
            </li>
          ))}
        </ul>
      )}
      {submission.files.length > 0 && (
        <ul className="fl-shots" aria-label="Screenshots">
          {submission.files.map((file, index) => (
            <li key={file.id} className="fl-shot">
              <a href={file.downloadUrl} target="_blank" rel="noopener noreferrer">
                <img src={file.downloadUrl} alt={`Screenshot ${index + 1}`} loading="lazy" />
              </a>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/**
 * One row per sent round, newest first, with the brand's decision on it when there is one. Only the newest row can
 * start open, so even three rounds stay short; the older ones open on a click.
 */
export function ProofRounds({
  versions,
  openLatest,
  reviews = [],
}: {
  versions: ProofSubmission[]
  openLatest: boolean
  reviews?: ProofReview[]
}) {
  return (
    <div className="fl-stack">
      {versions.map((version, index) => {
        const review = reviews.find((r) => r.submissionId === version.id)
        return (
          <details key={version.id} className="fl-round" open={openLatest && index === 0}>
            <summary>
              <span>
                <b>Round {version.round}</b> · sent {formatWhen(version.submittedAt)}
              </span>
              {review && <DecisionChip decision={review.decision} />}
            </summary>
            <div className="fl-round__body">
              {review?.reason && <p className="fl-quote">{review.reason}</p>}
              <ProofVersion submission={version} />
            </div>
          </details>
        )
      })}
    </div>
  )
}
