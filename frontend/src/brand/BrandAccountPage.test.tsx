import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { BrandAccountPage } from './BrandAccountPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

beforeEach(() => apiFetchMock.mockReset())

it('lists existing branches and adds another branch', async () => {
  const user = userEvent.setup()
  apiFetchMock
    .mockResolvedValueOnce([{ id: 11, name: 'Hamra', location: 'Beirut' }])
    .mockResolvedValueOnce({ id: 12, name: 'Jounieh', location: 'Keserwan' })

  render(<BrandAccountPage />)

  expect(await screen.findByText('Hamra — Beirut')).toBeInTheDocument()
  await user.type(screen.getByLabelText('Branch name'), 'Jounieh')
  await user.type(screen.getByLabelText('Location'), 'Keserwan')
  await user.click(screen.getByRole('button', { name: 'Add branch' }))

  expect(apiFetchMock).toHaveBeenLastCalledWith('/api/brand/branches', {
    method: 'POST',
    body: { name: 'Jounieh', location: 'Keserwan' },
  })
  expect(await screen.findByText('Jounieh — Keserwan')).toBeInTheDocument()
})
