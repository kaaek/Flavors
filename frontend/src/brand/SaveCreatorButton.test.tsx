import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { saveCreator, removeSavedCreator } from '../api/savedCreators'
import { SaveCreatorButton } from './SaveCreatorButton'

vi.mock('../api/savedCreators', () => ({
  saveCreator: vi.fn(),
  removeSavedCreator: vi.fn(),
}))

const saveMock = vi.mocked(saveCreator)
const removeMock = vi.mocked(removeSavedCreator)

beforeEach(() => {
  saveMock.mockReset()
  removeMock.mockReset()
})

it('displays Save when creator is not saved', () => {
  render(<SaveCreatorButton creatorId={5} initiallySaved={false} />)

  expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
})

it('saves and then unsaves a creator', async () => {
  const user = userEvent.setup()
  saveMock.mockResolvedValue(undefined)
  removeMock.mockResolvedValue(undefined)

  render(<SaveCreatorButton creatorId={5} initiallySaved={false} />)

  await user.click(screen.getByRole('button', { name: 'Save' }))

  expect(saveMock).toHaveBeenCalledWith(5)
  expect(await screen.findByRole('button', { name: 'Unsave' })).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Unsave' }))

  expect(removeMock).toHaveBeenCalledWith(5)
  expect(await screen.findByRole('button', { name: 'Save' })).toBeInTheDocument()
})

it('displays Unsave when creator is already saved', () => {
  render(<SaveCreatorButton creatorId={5} initiallySaved={true} />)

  expect(screen.getByRole('button', { name: 'Unsave' })).toBeInTheDocument()
})

it('shows an error if saving fails', async () => {
  const user = userEvent.setup()
  saveMock.mockRejectedValue(new Error('Network error'))

  render(<SaveCreatorButton creatorId={5} initiallySaved={false} />)

  await user.click(screen.getByRole('button', { name: 'Save' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Could not update saved creator. Please try again.',
  )
  expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
})
