import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { LoginPage } from './LoginPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))

const refresh = vi.fn()
vi.mock('./useMe', () => ({ useMe: () => ({ refresh }) }))
const apiFetchMock = vi.mocked(apiFetch)

beforeEach(() => {
  apiFetchMock.mockReset()
  refresh.mockReset()
})

it('opens the dashboard for the returned role', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValue({ id: 7, email: 'karim@example.com', role: 'BRAND' })
  refresh.mockResolvedValue(undefined)
  render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/brand" element={<p>Brand dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  )

  await user.type(screen.getByLabelText('Email'), 'karim@example.com')
  await user.type(screen.getByLabelText('Password'), 'right-password')
  await user.click(screen.getByRole('button', { name: 'Log in' }))

  expect(apiFetchMock).toHaveBeenCalledWith('/api/public/auth/login', {
    method: 'POST',
    body: { email: 'karim@example.com', password: 'right-password' },
  })
  expect(await screen.findByText('Brand dashboard')).toBeInTheDocument()
})

it('shows a connection message for an unexpected error', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockRejectedValue(new Error('network unavailable'))
  render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  )

  await user.type(screen.getByLabelText('Email'), 'karim@example.com')
  await user.type(screen.getByLabelText('Password'), 'wrong-password')
  await user.click(screen.getByRole('button', { name: 'Log in' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Could not reach the server. Please try again.',
  )
})
