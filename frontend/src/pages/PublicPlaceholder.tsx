import { Link } from 'react-router-dom'

/** A public page whose story hasn't been built yet (storefront, diner offer): honest, and always a way out. */
export function PublicPlaceholder({ title, note }: { title: string; note: string }) {
  return (
    <main className="fl-page">
      <div className="fl-card fl-empty">
        <img
          className="fl-empty__art-img"
          src="/illustrations/spots/empty-creators.svg"
          alt=""
          aria-hidden="true"
        />
        <h1>{title}</h1>
        <p>{note}</p>
        <Link className="fl-btn fl-btn--outline" to="/">
          Back to the start page
        </Link>
      </div>
    </main>
  )
}
