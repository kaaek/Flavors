import { useParams } from 'react-router-dom'
import { useFetch } from '../layout/useFetch'
import type { Storefront } from '../api/storefront'
import './creatorStorefront.css'

const dayLabel = (day: string) => day.slice(0, 3).toLowerCase().replace(/^./, (c) => c.toUpperCase())

/** Public, no account required. Claiming an offer is deliberately reserved for EEC-42. */
export function CreatorStorefrontPage() {
  const { slug = '' } = useParams<{ slug: string }>()
  return <StorefrontContent key={slug} slug={slug} />
}

function StorefrontContent({ slug }: { slug: string }) {
  const result = useFetch<Storefront>(`/api/public/storefronts/${encodeURIComponent(slug)}`)

  if (result.status === 'loading')
    return <main className="storefront-shell" aria-busy="true">Loading storefront…</main>
  if (result.status === 'error')
    return <main className="storefront-shell"><h1>Storefront not available</h1><p>This creator storefront could not be found.</p></main>

  const { data } = result
  return (
    <main className="storefront-shell">
      <header className="storefront-header">
        <span className="storefront-eyebrow">Flavors · Creator offers</span>
        <h1>{data.displayName}</h1>
        <p>{data.foodCategory} · {data.location}</p>
        {data.bio && <p>{data.bio}</p>}
      </header>
      <section aria-label="Live offers">
        <h2>Restaurant offers</h2>
        {data.offers.length === 0 ? (
          <div className="storefront-empty"><h3>No offers yet</h3><p>Check back for new restaurant offers.</p></div>
        ) : (
          <div className="storefront-offers">
            {data.offers.map((offer) => (
              <article className="storefront-offer" key={offer.id}>
                <span className="storefront-discount">{offer.discountPercent}% off</span>
                <h3>{offer.restaurant}</h3>
                <p>Valid until {offer.expiresOn}</p>
                <p>Valid days: {offer.validDays.map(dayLabel).join(', ')}</p>
                <p>Hours: {offer.validFrom && offer.validTo ? `${offer.validFrom}–${offer.validTo}` : 'All day'}</p>
                <p>Minimum purchase: ${(offer.minPurchaseCents / 100).toFixed(2)}</p>
                <p>Limit: {offer.perPersonLimit} per person</p>
                <div className="storefront-locations">
                  <strong>Participating branches</strong>
                  {offer.branches.length > 0 ? (
                    <ul>{offer.branches.map((branch, index) => <li key={`${index}-${branch.name}`}>{branch.name} · {branch.location}</li>)}</ul>
                  ) : (
                    <p>Ask the restaurant for participating locations.</p>
                  )}
                </div>
                <p className="storefront-note">Offer claiming and personal QR codes are coming soon.</p>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}
