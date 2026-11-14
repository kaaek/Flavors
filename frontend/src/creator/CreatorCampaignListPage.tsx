
import { Link } from 'react-router-dom'
import { listCreatorCampaigns, type CreatorCampaignSummary } from '../api/campaigns'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { formatUSD } from '../lib/money'
import { CampaignStatusChip } from '../brand/CampaignStatusChip'

/** Campaign requests the creator has received from brands. */
export function CreatorCampaignListPage() {
  const campaigns = useFetchWith<CreatorCampaignSummary[]>(listCreatorCampaigns)

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>My campaigns</h1>
          <p>Campaign requests and offers received from brands.</p>
        </div>
      </header>

      {campaigns.status === 'error' && (
        <div className="fl-alert fl-alert--danger" role="alert">
          <Icon name="alert" />
          <div>Could not load your campaigns. Please try again.</div>
        </div>
      )}

      {campaigns.status === 'loading' && (
        <div className="fl-card" aria-busy="true" aria-label="Loading campaigns">
          <div className="fl-skel fl-skel--line" />
        </div>
      )}

      {campaigns.status === 'ready' && campaigns.data.length === 0 && (
        <div className="fl-card fl-empty">
          <h2>No campaigns yet</h2>
          <p>When a brand sends you a campaign request, it will appear here.</p>
        </div>
      )}

      {campaigns.status === 'ready' && campaigns.data.length > 0 && (
        <div className="fl-tablewrap">
          <table className="fl-table">
            <thead>
              <tr>
                <th scope="col">Campaign</th>
                <th scope="col">Brand</th>
                <th scope="col">Price offered</th>
                <th scope="col">Agreed price</th>
                <th scope="col">Status</th>
                <th scope="col">
                  <span className="fl-sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {campaigns.data.map((campaign) => (
                <tr key={campaign.id}>
                  <td>{campaign.name}</td>
                  <td>{campaign.brandName}</td>
                  <td>
                    {campaign.priceOfferedCents === null
                      ? '—'
                      : formatUSD(campaign.priceOfferedCents)}
                  </td>
                  <td>
                    {campaign.agreedPriceCents === null
                      ? '—'
                      : formatUSD(campaign.agreedPriceCents)}
                  </td>
                  <td>
                    <CampaignStatusChip status={campaign.status} />
                  </td>
                  <td>
                    <Link
                      className="fl-btn fl-btn--outline fl-btn--sm"
                      to={`/creator/campaigns/${campaign.id}`}
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
