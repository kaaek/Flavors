import type { ProofSubmission } from '../api/proof'
import { formatWhen } from './dates'
import './proof.css'

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
 * One row per sent round, newest first. Only the newest row can start open, so even three rounds stay short; the
 * older ones open on a click.
 */
export function ProofRounds({
  versions,
  openLatest,
}: {
  versions: ProofSubmission[]
  openLatest: boolean
}) {
  return (
    <div className="fl-stack">
      {versions.map((version, index) => (
        <details key={version.id} className="fl-round" open={openLatest && index === 0}>
          <summary>
            <span>
              <b>Round {version.round}</b> · sent {formatWhen(version.submittedAt)}
            </span>
          </summary>
          <div className="fl-round__body">
            <ProofVersion submission={version} />
          </div>
        </details>
      ))}
    </div>
  )
}
