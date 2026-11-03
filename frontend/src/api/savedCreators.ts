import { apiFetch } from './client'
export interface SavedCreator {
  creatorId: number
  savedAt: string
}
export function getSavedCreators() {
  return apiFetch<SavedCreator[]>('/api/brand/saved-creators')
}
export function saveCreator(creatorId: number) {
  return apiFetch<void>(`/api/brand/saved-creators/${creatorId}`, {
    method: 'POST',
  })
}
export function removeSavedCreator(creatorId: number) {
  return apiFetch<void>(`/api/brand/saved-creators/${creatorId}`, {
    method: 'DELETE',
  })
}
