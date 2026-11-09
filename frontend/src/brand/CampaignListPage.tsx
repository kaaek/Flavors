import { Link } from 'react-router-dom'
import { listCampaigns, type CampaignSummary } from '../api/campaigns'
import { Icon } from '../layout/Icon'
import { useFetchWith } from '../layout/useFetch'
import { formatUSD } from '../lib/money'
import { CampaignStatusChip } from './CampaignStatusChip'

/** The brand's campaigns: name, creator and status, with Edit on drafts (UC-F3.1). */
export function CampaignListPage() {
  const campaigns = useFetchWith<CampaignSummary[]>(listCampaigns)

  return (
    <div className="fl-stack">
      <header className="fl-pagehead">
        <div>
          <h1>Campaigns</h1>
          <p>The requests you have drafted and sent to creators.</p>
        </div>
        <Link className="fl-btn fl-btn--primary" to="/brand/campaigns/new">
          <Icon name="plus" />
          New campaign
        </Link>
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
          <img
            className="fl-empty__art-img"
            src="/illustrations/spots/empty-campaigns.svg"
            alt=""
            aria-hidden="true"
          />
          <h2>No campaigns yet</h2>
          <p>Start one to tell a creator what you want and what you offer.</p>
          <Link className="fl-btn fl-btn--primary" to="/brand/campaigns/new">
            New campaign
          </Link>
        </div>
      )}

      {campaigns.status === 'ready' && campaigns.data.length > 0 && (
        <div className="fl-tablewrap">
          <table className="fl-table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Creator</th>
                <th scope="col">Price offered</th>
                <th scope="col">Status</th>
                <th scope="col">
                  <span className="fl-sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {campaigns.data.map((campaign) => (
                <tr key={campaign.id}>
                  <td>
                    {/* A draft opens in the form; a sent campaign opens read-only. */}
                    <Link
                      className="fl-link"
                      to={
                        campaign.status === 'DRAFT'
                          ? `/brand/campaigns/${campaign.id}/edit`
                          : `/brand/campaigns/${campaign.id}`
                      }
                    >
                      {campaign.name}
                    </Link>
                  </td>
                  <td>{campaign.creatorName ?? 'Not chosen yet'}</td>
                  <td>
                    {campaign.priceOfferedCents === null
                      ? '—'
                      : formatUSD(campaign.priceOfferedCents)}
                  </td>
                  <td>
                    <CampaignStatusChip status={campaign.status} />
                  </td>
                  <td>
                    {campaign.status === 'DRAFT' && (
                      <Link
                        className="fl-btn fl-btn--ghost fl-btn--sm"
                        to={`/brand/campaigns/${campaign.id}/edit`}
                        aria-label={`Edit ${campaign.name}`}
                      >
                        Edit
                      </Link>
                    )}
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
