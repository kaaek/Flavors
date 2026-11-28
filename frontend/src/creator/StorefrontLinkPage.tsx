import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Storefront } from '../api/storefront'
import { useFetch } from '../layout/useFetch'

/** Approved creators can copy a stable, anonymous link to their storefront. */
export function StorefrontLinkPage() {
  const result = useFetch<Storefront>('/api/creator/storefront')
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState(false)
  if (result.status === 'loading')
    return (
      <div className="fl-card" aria-busy="true">
        Loading storefront…
      </div>
    )
  if (result.status === 'error')
    return (
      <div className="fl-card fl-stack">
        <h1>Your storefront</h1>
        <p>Your shareable storefront becomes available after your creator profile is approved.</p>
      </div>
    )
  const path = `/s/${result.data.slug}`
  const url = `${window.location.origin}${path}`
  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setCopyError(false)
    } catch {
      setCopyError(true)
    }
  }
  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Your storefront</h1>
          <p>Share your restaurant offers with followers.</p>
        </div>
      </header>
      <div className="fl-card fl-stack">
        <label htmlFor="shareable-storefront-url">Public storefront link</label>
        <input
          id="shareable-storefront-url"
          readOnly
          value={url}
          onFocus={(e) => e.target.select()}
        />
        <div className="fl-row">
          <button className="fl-btn fl-btn--primary" type="button" onClick={copyLink}>
            Copy storefront link
          </button>
          <Link className="fl-btn fl-btn--outline" to={path}>
            Preview storefront
          </Link>
        </div>
        {copied && <p role="status">Link copied!</p>}
        {copyError && (
          <p role="alert">Could not copy automatically. Select and copy the link above.</p>
        )}
        <p>
          {result.data.offers.length} live {result.data.offers.length === 1 ? 'offer' : 'offers'}{' '}
          available.
        </p>
      </div>
    </div>
  )
}
