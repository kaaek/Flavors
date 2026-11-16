import { useCallback, useState, type DragEvent } from 'react'
import { ApiError } from '../api/client'
import {
  PROOF_LIMITS,
  getCreatorProof,
  saveProofDraft,
  submitProof,
  submittedVersions,
  type DeliveryProof,
  type ProofDraftChange,
} from '../api/proof'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { LOCALE } from '../lib/locale'
import { ProofRounds } from '../proof/ProofRounds'
import { ReviewTimer } from '../proof/ReviewTimer'
import { formatDay, formatWhen } from '../proof/dates'
import '../proof/proof.css'

/** The first version plus at most two correction rounds (V5 proof_submission.round). */
const MAX_ROUNDS = 3

const savedTime = new Intl.DateTimeFormat(LOCALE, { timeStyle: 'short' })

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Could not reach the server. Please try again.'
}

/** The same rule the backend applies to a final link, so the creator sees the problem before sending. */
function isWebLink(value: string): boolean {
  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname !== ''
  } catch {
    return false
  }
}

const cleanLinks = (rows: string[]) => rows.map((row) => row.trim()).filter((row) => row !== '')

/** Checked in the browser first; the backend checks the real file signature again. */
function problemWith(files: File[], alreadyThere: number): string | null {
  const room = PROOF_LIMITS.files - alreadyThere
  if (files.length > room) {
    return room === 0
      ? `A proof can have at most ${PROOF_LIMITS.files} screenshots. Remove one to add another.`
      : `A proof can have at most ${PROOF_LIMITS.files} screenshots. You can add ${room} more.`
  }
  if (
    files.some(
      (file) => file.type !== '' && !(PROOF_LIMITS.types as readonly string[]).includes(file.type),
    )
  ) {
    return 'Screenshots must be PNG, JPEG, GIF or WebP images.'
  }
  if (files.some((file) => file.size > PROOF_LIMITS.fileBytes)) {
    return 'Each screenshot must be 5 MB or smaller.'
  }
  return null
}

/** The rows the form starts with: the draft's links, or for a correction round the last version's, to edit. */
function startingLinks(proof: DeliveryProof): string[] {
  const draft = proof.submissions.find((submission) => submission.status === 'DRAFT')
  const latest = submittedVersions(proof)[0]
  const links = draft
    ? draft.links
    : proof.campaignStatus === 'CORRECTIONS_REQUESTED' && latest
      ? latest.links
      : []
  return links.length > 0 ? links : ['']
}

/**
 * UC-F5.1 for the creator: prepare one draft per round (links, optional screenshots), send it once, and see every
 * sent version. Shown on the campaign page from the moment the campaign is ACTIVE.
 */
export function CreatorProofSection({
  campaignId,
  lastPostingDate,
  onSubmitted,
}: {
  campaignId: number
  lastPostingDate: string | null
  onSubmitted: () => void
}) {
  const load = useCallback(() => getCreatorProof(campaignId), [campaignId])
  const result = useFetchWith<DeliveryProof>(load)

  const [updated, setUpdated] = useState<DeliveryProof | null>(null)
  // null until the creator edits or saves; until then the rows come from the loaded proof.
  const [editedLinks, setEditedLinks] = useState<string[] | null>(null)
  const [dirty, setDirty] = useState(false)
  const [savedAt, setSavedAt] = useState<Date | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

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
        <div>Could not load your delivery proof. Please reload the page.</div>
      </div>
    )
  }

  const proof = updated ?? result.data
  const status = proof.campaignStatus
  const editable = status === 'ACTIVE' || status === 'CORRECTIONS_REQUESTED'
  const draft = proof.submissions.find((submission) => submission.status === 'DRAFT')
  const versions = submittedVersions(proof)
  const latest = versions[0]
  const round = draft?.round ?? (latest?.round ?? 0) + 1
  const links = editedLinks ?? startingLinks(proof)
  const files = draft?.files ?? []

  function editLinks(next: string[]) {
    setEditedLinks(next)
    setDirty(true)
  }

  async function save(change: ProofDraftChange, newFiles: File[] = []) {
    const shown = links
    setBusy(true)
    setError(null)
    try {
      setUpdated(await saveProofDraft(campaignId, change, newFiles))
      setEditedLinks(shown)
      setDirty(false)
      setSavedAt(new Date())
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  function addFiles(list: FileList | null) {
    const picked = list ? Array.from(list) : []
    if (picked.length === 0) return
    const problem = problemWith(picked, files.length)
    if (problem) {
      setError(problem)
      return
    }
    // Every save sends the links on screen too, so "saved as soon as you upload" never drops typed links.
    void save({ links: cleanLinks(links) }, picked)
  }

  function onDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    if (!busy) addFiles(event.dataTransfer.files)
  }

  function startSending() {
    const final = cleanLinks(links)
    if (final.length === 0) {
      setError('Add at least one link to your posts before sending.')
      return
    }
    if (!final.every(isWebLink)) {
      setError('Every link must be a full web address, starting with https://')
      return
    }
    setError(null)
    setConfirming(true)
  }

  async function send() {
    setBusy(true)
    setError(null)
    try {
      setUpdated(await submitProof(campaignId, { links: cleanLinks(links) }))
      setEditedLinks(null)
      setDirty(false)
      setConfirming(false)
      onSubmitted()
    } catch (e) {
      setError(messageOf(e))
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  const saveState = dirty
    ? 'Unsaved changes'
    : savedAt
      ? `Draft saved ${savedTime.format(savedAt)}`
      : draft
        ? 'Draft saved'
        : null

  if (!editable) {
    return (
      <section className="fl-card fl-section fl-stack" aria-labelledby="proof-title">
        <h2 id="proof-title">Delivery proof</h2>
        {status === 'PROOF_SUBMITTED' && latest && proof.reviewDeadline && (
          <>
            <p className="fl-muted">
              Sent on {formatWhen(latest.submittedAt)}. The brand reviews it by{' '}
              {formatWhen(proof.reviewDeadline)}.
            </p>
            <ReviewTimer deadline={proof.reviewDeadline} label="Brand review of proof" />
          </>
        )}
        {versions.length > 0 ? (
          <ProofRounds versions={versions} openLatest />
        ) : (
          <p className="fl-muted">No proof was sent for this campaign.</p>
        )}
      </section>
    )
  }

  return (
    <>
      <section className="fl-card fl-section fl-stack" aria-labelledby="proof-title">
        <div className="fl-row fl-row--between">
          <h2 id="proof-title">Delivery proof</h2>
          <span className="fl-hint">
            Round {round} of {MAX_ROUNDS}
            {saveState && ` · ${saveState}`}
          </span>
        </div>
        <p className="fl-hint">
          {status === 'CORRECTIONS_REQUESTED'
            ? 'Send a new version with the changes the brand asked for.'
            : `Post by ${formatDay(lastPostingDate)}, then add the links to your posts. The brand sees nothing until you send it.`}
        </p>

        <fieldset className="fl-field fl-fieldset">
          <legend className="fl-label">Links to your posts</legend>
          <ul className="fl-prooflinks">
            {links.map((link, index) => (
              <li key={index} className="fl-linkrow">
                <input
                  className="fl-input"
                  type="url"
                  inputMode="url"
                  aria-label={`Link ${index + 1}`}
                  placeholder="https://www.instagram.com/reel/…"
                  value={link}
                  disabled={busy}
                  onChange={(event) =>
                    editLinks(links.map((row, i) => (i === index ? event.target.value : row)))
                  }
                />
                <button
                  className="fl-iconbtn"
                  type="button"
                  aria-label={`Remove link ${index + 1}`}
                  disabled={busy || (links.length === 1 && link === '')}
                  onClick={() =>
                    editLinks(links.length === 1 ? [''] : links.filter((_, i) => i !== index))
                  }
                >
                  <Icon name="x" />
                </button>
              </li>
            ))}
          </ul>
          <div>
            <button
              className="fl-btn fl-btn--ghost fl-btn--sm"
              type="button"
              disabled={busy || links.length >= PROOF_LIMITS.links}
              onClick={() => editLinks([...links, ''])}
            >
              <Icon name="plus" /> Add link
            </button>
          </div>
          <span className="fl-hint">
            Up to {PROOF_LIMITS.links} links. Use the full link, starting with https://
          </span>
        </fieldset>

        <div className="fl-stack">
          <span className="fl-label">
            Screenshots <small>(optional)</small>
          </span>
          <label
            className="fl-dropzone"
            onDragOver={(event) => event.preventDefault()}
            onDrop={onDrop}
          >
            <Icon name="upload" />
            <span>
              <b>Choose files</b> or drop screenshots here
            </span>
            <span className="fl-hint">
              PNG, JPEG, GIF or WebP, up to 5 MB each, at most {PROOF_LIMITS.files}. Saved as soon
              as you upload.
            </span>
            <input
              className="fl-sr-only"
              type="file"
              multiple
              accept={PROOF_LIMITS.types.join(',')}
              aria-label="Add screenshots"
              disabled={busy || files.length >= PROOF_LIMITS.files}
              onChange={(event) => {
                addFiles(event.target.files)
                event.target.value = ''
              }}
            />
          </label>
          {files.length > 0 && (
            <ul className="fl-shots" aria-label="Uploaded screenshots">
              {files.map((file, index) => (
                <li key={file.id} className="fl-shot">
                  <img src={file.downloadUrl} alt={`Screenshot ${index + 1}`} />
                  <button
                    className="fl-iconbtn"
                    type="button"
                    aria-label={`Remove screenshot ${index + 1}`}
                    disabled={busy}
                    onClick={() =>
                      void save({
                        links: cleanLinks(links),
                        retainedFileIds: files.filter((f) => f.id !== file.id).map((f) => f.id),
                      })
                    }
                  >
                    <Icon name="x" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {error && (
          <div className="fl-alert fl-alert--danger" role="alert">
            <Icon name="alert" />
            <div>{error}</div>
          </div>
        )}

        {confirming ? (
          <div className="fl-confirm" role="group" aria-labelledby="proof-confirm-title">
            <h3 id="proof-confirm-title">Send your proof?</h3>
            <p>The brand gets 5 days to review it. You can't edit this version after sending.</p>
            <div className="fl-summary">
              <div>
                <span>Links</span>
                <span>{cleanLinks(links).length}</span>
              </div>
              <div>
                <span>Screenshots</span>
                <span>{files.length}</span>
              </div>
              <div>
                <span>Round</span>
                <span>
                  {round} of {MAX_ROUNDS}
                </span>
              </div>
            </div>
            <div className="fl-actions">
              <button
                className="fl-btn fl-btn--ghost"
                type="button"
                disabled={busy}
                onClick={() => setConfirming(false)}
              >
                Back
              </button>
              <button
                className="fl-btn fl-btn--primary"
                type="button"
                disabled={busy}
                onClick={() => void send()}
              >
                {busy ? 'Sending…' : 'Send proof'}
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="fl-actions">
              <button
                className="fl-btn fl-btn--outline"
                type="button"
                disabled={busy}
                onClick={() => void save({ links: cleanLinks(links) })}
              >
                Save draft
              </button>
              <button
                className="fl-btn fl-btn--primary"
                type="button"
                disabled={busy}
                onClick={startSending}
              >
                Send proof
              </button>
            </div>
            <p className="fl-hint">
              Once sent, this version can't be edited. The brand then has 5 days to review it.
            </p>
          </>
        )}
      </section>

      {versions.length > 0 && (
        <section className="fl-card fl-section fl-stack" aria-labelledby="proof-history-title">
          <h2 id="proof-history-title">Proof history</h2>
          <ProofRounds versions={versions} openLatest={false} />
        </section>
      )}
    </>
  )
}
