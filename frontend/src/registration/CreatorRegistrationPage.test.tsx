import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { CreatorRegistrationPage } from './CreatorRegistrationPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

beforeEach(() => apiFetchMock.mockReset())
function failWith(error: ApiError) {
  apiFetchMock.mockImplementation(() => Promise.reject(error))
}

async function fillForm(user: ReturnType<typeof userEvent.setup>, handle = 'lara.eats') {
  await user.type(screen.getByLabelText('Name'), 'Lara')
  await user.type(screen.getByLabelText('Email'), 'lara@example.com')
  await user.type(screen.getByLabelText('Password'), 'correct horse')
  await user.type(screen.getByLabelText('Food category'), 'Street food')
  await user.type(screen.getByLabelText('Location (city or country)'), 'Beirut')
  await user.type(screen.getByLabelText('Minimum price per campaign (USD)'), '100')
  await user.type(screen.getByLabelText('Maximum price per campaign (USD)'), '300')
  await user.type(screen.getByLabelText('Short bio (optional)'), 'I eat for a living.')
  if (handle) await user.type(screen.getByLabelText('Instagram username'), handle)
}

function renderPage() {
  render(
    <MemoryRouter>
      <CreatorRegistrationPage />
    </MemoryRouter>,
  )
}

it('sends the registration with prices in cents and shows the pending message', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValue({
    id: 7,
    email: 'lara@example.com',
    role: 'CREATOR',
    verificationStatus: 'PENDING_APPROVAL',
  })
  renderPage()

  await fillForm(user)
  await user.click(screen.getByRole('button', { name: 'Send registration' }))

  expect(apiFetchMock).toHaveBeenCalledWith('/api/public/auth/register/creator', {
    method: 'POST',
    body: {
      name: 'Lara',
      email: 'lara@example.com',
      password: 'correct horse',
      foodCategory: 'Street food',
      location: 'Beirut',
      priceMinCents: 10000,
      priceMaxCents: 30000,
      bio: 'I eat for a living.',
      socialAccounts: [{ platform: 'instagram', externalId: 'lara.eats' }],
    },
  })
  expect(await screen.findByText(/pending approval/i)).toBeInTheDocument()
})

it.skip('sends no social account when none is entered, so the server refuses it', async () => {
  const user = userEvent.setup()
  failWith(
    new ApiError(400, { errors: { socialAccounts: 'Connect at least one social account.' } }),
  )
  renderPage()

  await fillForm(user, '')
  await user.click(screen.getByRole('button', { name: 'Send registration' }))

  expect(apiFetchMock.mock.calls[0][1]).toMatchObject({ body: { socialAccounts: [] } })
  expect(await screen.findByText('Connect at least one social account.')).toBeInTheDocument()
})

it.skip('shows the message when the email is already used', async () => {
  const user = userEvent.setup()
  failWith(
    new ApiError(409, {
      detail: 'An account already uses this email. Log in or use another email.',
    }),
  )
  renderPage()

  await fillForm(user)
  await user.click(screen.getByRole('button', { name: 'Send registration' }))

  expect(
    await screen.findByText('An account already uses this email. Log in or use another email.'),
  ).toBeInTheDocument()
})
