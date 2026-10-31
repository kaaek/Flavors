import { useState } from "react"
import { saveCreator, removeSavedCreator } from "../api/savedCreators"

interface SaveCreatorButtonProps {
  creatorId: number
  initiallySaved: boolean
  onChange?: (saved: boolean) => void
}

export function SaveCreatorButton({ creatorId, initiallySaved, onChange }: SaveCreatorButtonProps) {
  const [isSaved, setIsSaved] = useState(initiallySaved)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  async function toggleSave() {
    if (loading) return

    setLoading(true)
    setError("")

    try {
      if (isSaved) {
        await removeSavedCreator(creatorId)
        setIsSaved(false)
        onChange?.(false)
      } else {
        await saveCreator(creatorId)
        setIsSaved(true)
        onChange?.(true)
      }
    } catch {
      setError("Could not update saved creator. Please try again.")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <button type='button' onClick={toggleSave} disabled={loading} aria-pressed={isSaved}>
        {loading ? "Saving..." : isSaved ? "Unsave" : "Save"}
      </button>
      {error && <p role='alert'>{error}</p>}
    </div>
  )
}
