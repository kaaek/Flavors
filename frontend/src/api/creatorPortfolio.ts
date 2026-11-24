import { apiFetch } from './client'

export interface CreatorPortfolio {
  creatorId: number
  displayName: string
  bio: string | null
  foodCategory: string | null
  location: string | null
  priceMinCents: number | null
  priceMaxCents: number | null
  socialAccounts: {
    platform: string
    followers: number | null
    engagementRate: number | null
    lastPulledAt: string | null
  }[]
  featuredCampaigns: {
    campaignId: number
    name: string
    verified: boolean
    redemptions: number
    roiPercent: number | null
  }[]
  averageRating: number | null
  reviewCount: number
  reviews: { rating: number; text: string | null; createdAt: string }[]
}

export const getCreatorPortfolio = (creatorId: string) =>
  apiFetch<CreatorPortfolio>(`/api/brand/creators/${encodeURIComponent(creatorId)}/portfolio`)
