import { apiFetch } from './client'
import { toQueryString, type SearchPage } from './creatorSearch'

export interface BrandSearchParams {
  q?: string
  cuisine?: string
  location?: string
  page?: number
  size?: number
}

/** No phone or contact person: the API keeps those private until a campaign links the two sides. */
export interface BrandSearchItem {
  brandId: number
  businessName: string
  cuisine: string | null
  description: string | null
  branchLocations: string[]
}

export function searchBrands(params: BrandSearchParams) {
  return apiFetch<SearchPage<BrandSearchItem>>(`/api/creator/brands/search${toQueryString(params)}`)
}
