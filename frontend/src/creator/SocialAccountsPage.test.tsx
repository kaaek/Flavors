import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { SocialAccountsPage, type SocialAccount } from './SocialAccountsPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

beforeEach(() => apiFetchMock.mockReset())

const CONNECTED: SocialAccount = {
  id: 3,
  platform: 'instagram',
  status: 'CONNECTED',
  followers: 12000,
  engagementRate: 3,
  lastPulledAt: '2026-11-03T10:00:00Z',
}

function renderAt(path: string, goTo = vi.fn()) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <SocialAccountsPage goTo={goTo} />
    </MemoryRouter>,
  )
  return goTo
}

it('lists her accounts with the numbers pulled from Instagram', async () => {
  apiFetchMock.mockResolvedValueOnce([CONNECTED])

  renderAt('/creator/social')

  expect(await screen.findByText('12,000')).toBeInTheDocument()
  expect(screen.getByText('3.00%')).toBeInTheDocument()
  expect(screen.getByText(/Last updated/)).toBeInTheDocument()
  expect(screen.getByText('Connected')).toBeInTheDocument()
  expect(apiFetchMock).toHaveBeenCalledWith('/api/creator/social')
})

it('sends the browser to Instagram when she clicks Connect Instagram', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValueOnce([]).mockResolvedValueOnce({
    authorizationUrl: 'https://www.instagram.com/oauth/authorize?state=x',
  })

  const goTo = renderAt('/creator/social')
  expect(await screen.findByText('No account connected yet.')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Connect Instagram' }))

  expect(apiFetchMock).toHaveBeenLastCalledWith('/api/creator/social/instagram/connect', {
    method: 'POST',
  })
  expect(goTo).toHaveBeenCalledWith('https://www.instagram.com/oauth/authorize?state=x')
})

// TC-F19.4.1 / TC-F19.4.2: the page says how the round trip through Instagram ended.
it('says the account is connected when Instagram sends her back', async () => {
  apiFetchMock.mockResolvedValueOnce([CONNECTED])

  renderAt('/creator/social?instagram=connected')

  expect(await screen.findByRole('status')).toHaveTextContent(
    'Your Instagram account is connected.',
  )
})

it('says nothing was connected when she cancelled on Instagram', async () => {
  apiFetchMock.mockResolvedValueOnce([])

  renderAt('/creator/social?instagram=denied')

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'You cancelled on Instagram, so nothing was connected.',
  )
  expect(screen.getByRole('button', { name: 'Connect Instagram' })).toBeEnabled()
})

it('shows the server message when connecting is not available', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValueOnce([]).mockRejectedValueOnce(
    new ApiError(503, {
      title: 'Instagram not set up',
      detail: 'Connecting Instagram is not available yet.',
    }),
  )

  const goTo = renderAt('/creator/social')
  await screen.findByText('No account connected yet.')
  await user.click(screen.getByRole('button', { name: 'Connect Instagram' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Connecting Instagram is not available yet.',
  )
  expect(goTo).not.toHaveBeenCalled()
})

// FR-F19.16: after a disconnect the numbers stay and the account shows Needs reconnection.
it('disconnecting keeps the numbers and shows Needs reconnection', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValueOnce([CONNECTED]).mockResolvedValueOnce(undefined)

  renderAt('/creator/social')
  await user.click(await screen.findByRole('button', { name: 'Disconnect Instagram' }))

  expect(apiFetchMock).toHaveBeenLastCalledWith('/api/creator/social/3', { method: 'DELETE' })
  expect(await screen.findByText('Needs reconnection')).toBeInTheDocument()
  expect(screen.getByText('12,000')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Reconnect Instagram' })).toBeInTheDocument()
})
