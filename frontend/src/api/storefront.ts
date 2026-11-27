import { apiFetch } from './client'
import type { Day } from './offers'

export interface StorefrontOffer {
  id: number
  restaurant: string
  discountPercent: number
  expiresOn: string
  validDays: Day[]
  validFrom: string | null
  validTo: string | null
  minPurchaseCents: number
  perPersonLimit: number
  branches: { name: string; location: string }[]
}
export interface Storefront {
  displayName: string
  bio: string | null
  foodCategory: string
  location: string
  slug: string
  offers: StorefrontOffer[]
}

export const getStorefront = (slug: string) =>
  apiFetch<Storefront>(`/api/public/storefronts/${encodeURIComponent(slug)}`)
export const getOwnStorefront = () => apiFetch<Storefront>('/api/creator/storefront')
