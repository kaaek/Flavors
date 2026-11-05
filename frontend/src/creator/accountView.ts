/** What the portfolio and social-accounts APIs both say about a connected account. */
export interface AccountView {
  platform: string
  status: 'CONNECTED' | 'NEEDS_RECONNECTION'
  followers: number | null
  engagementRate: number | null
  lastPulledAt: string | null
}

export function platformName(platform: string) {
  return platform.toLowerCase() === 'instagram' ? 'Instagram' : platform
}
