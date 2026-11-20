import { apiFetch } from './client'

export interface CampaignReviewRequest {
  rating: number
  text: string | null
}

export interface CampaignReviewResponse {
  id: number
  campaignId: number
  rating: number
  text: string | null
  createdAt: string
}

export function submitCampaignReview(
  campaignId: number,
  request: CampaignReviewRequest,
): Promise<CampaignReviewResponse> {
  return apiFetch<CampaignReviewResponse>(`/api/brand/campaigns/${campaignId}/review`, {
    method: 'POST',
    body: request,
  })
}
export async function getCampaignReview(
  campaignId: number,
): Promise<CampaignReviewResponse | null> {
  const review = await apiFetch<CampaignReviewResponse | undefined>(
    `/api/brand/campaigns/${campaignId}/review`,
  )

  return review ?? null
}
