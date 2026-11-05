import { apiFetch } from './client'

/** Advanced filters of the creator search. Prices are integer cents, like everywhere in the API. */
export interface CreatorSearchParams {
  q?: string
  location?: string
  category?: string
  platform?: string
  minFollowers?: number
  maxFollowers?: number
  minEngagement?: number
  minPriceCents?: number
  maxPriceCents?: number
  minRoi?: number
  minRedemptions?: number
  page?: number
  size?: number
}

export interface CreatorSearchItem {
  creatorId: number
  displayName: string
  foodCategory: string | null
  location: string | null
  /** Platform, followers and engagement come from the creator's best connected account, so they are null until she connects one. */
  platform: string | null
  followers: number | null
  engagementRate: number | null
  priceMinCents: number | null
  priceMaxCents: number | null
  roi: number | null
  redemptions: number | null
  storefrontSlug: string | null
  saved: boolean
}

export interface SearchPage<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
}

export interface CreatorFacets {
  locations: string[]
  categories: string[]
}

/** Builds the query string; blank and undefined values are left out so the URL only carries what the brand set. */
export function toQueryString(params: object): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    query.set(key, String(value))
  }
  const text = query.toString()
  return text ? `?${text}` : ''
}

export function searchCreators(params: CreatorSearchParams) {
  return apiFetch<SearchPage<CreatorSearchItem>>(
    `/api/brand/creators/search${toQueryString(params)}`,
  )
}

export function getCreatorFacets() {
  return apiFetch<CreatorFacets>('/api/brand/creators/facets')
}
