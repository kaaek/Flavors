import { apiFetch } from '../api/client'

export type SocialAccountStatus = 'CONNECTED' | 'NEEDS_RECONNECTION'

export interface SocialAccountView {
  platform: string
  status: SocialAccountStatus
  followers: number | null
  engagementRate: number | null
  lastPulledAt: string | null
}

export interface CampaignView {
  id: number
  name: string
  featured: boolean
}

/** What GET /api/creator/portfolio returns. Prices are integer cents. */
export interface Portfolio {
  displayName: string
  bio: string | null
  foodCategory: string
  location: string
  priceMinCents: number
  priceMaxCents: number
  socialAccounts: SocialAccountView[]
  completedCampaigns: CampaignView[]
}

/** The body of PUT /api/creator/portfolio: a full replace of the details. */
export interface PortfolioDetails {
  bio: string | null
  foodCategory: string
  location: string
  priceMinCents: number
  priceMaxCents: number
}

const BASE = '/api/creator/portfolio'

export function getPortfolio() {
  return apiFetch<Portfolio>(BASE)
}

export function savePortfolioDetails(details: PortfolioDetails) {
  return apiFetch<Portfolio>(BASE, { method: 'PUT', body: details })
}

export function featureCampaign(campaignId: number) {
  return apiFetch<CampaignView>(`${BASE}/featured`, { method: 'POST', body: { campaignId } })
}

export function unfeatureCampaign(campaignId: number) {
  return apiFetch<void>(`${BASE}/featured/${campaignId}`, { method: 'DELETE' })
}
