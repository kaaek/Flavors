import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
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
  apiFetchMock.mockImplementation((path: string, options?: { method?: string }) => {
    if (path === '/api/brand/profile') return Promise.reject(new Error('no profile'))
    return Promise.resolve(
      options?.method === 'POST'
        ? { id: 12, name: 'Jounieh', location: 'Keserwan' }
        : [{ id: 11, name: 'Hamra', location: 'Beirut' }],
    )
  })

  render(
    <MemoryRouter>
      <BrandAccountPage />
    </MemoryRouter>,
  )

  expect(await screen.findByText('Hamra')).toBeInTheDocument()
  expect(screen.getByText('Beirut')).toBeInTheDocument()
  await user.type(screen.getByLabelText('Branch name'), 'Jounieh')
  await user.type(screen.getByLabelText('Location'), 'Keserwan')
  await user.click(screen.getByRole('button', { name: 'Add branch' }))

  expect(apiFetchMock).toHaveBeenLastCalledWith('/api/brand/branches', {
    method: 'POST',
    body: { name: 'Jounieh', location: 'Keserwan' },
  })
  expect(await screen.findByText('Jounieh')).toBeInTheDocument()
  expect(screen.getByText('Keserwan')).toBeInTheDocument()
})

it('shows the profile and saves a new cuisine and description', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockImplementation((path: string) =>
    Promise.resolve(
      path === '/api/brand/profile'
        ? { businessName: 'Karim', cuisine: 'Burgers', description: null }
        : [],
    ),
  )
  render(
    <MemoryRouter>
      <BrandAccountPage />
    </MemoryRouter>,
  )

  const cuisine = await screen.findByLabelText('Cuisine')
  expect(cuisine).toHaveValue('Burgers')
  await user.clear(cuisine)
  await user.type(cuisine, 'Lebanese')
  await user.type(screen.getByLabelText('About your restaurant'), 'Family recipes')
  await user.click(screen.getByRole('button', { name: 'Save profile' }))

  expect(apiFetchMock).toHaveBeenCalledWith('/api/brand/profile', {
    method: 'PATCH',
    body: { cuisine: 'Lebanese', description: 'Family recipes' },
  })
  expect(await screen.findByText('Profile saved.')).toBeInTheDocument()
})
