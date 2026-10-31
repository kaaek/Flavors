import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { BrandRegistrationPage } from './BrandRegistrationPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

beforeEach(() => apiFetchMock.mockReset())

it('registers the brand with every entered branch', async () => {
  const user = userEvent.setup()
  apiFetchMock.mockResolvedValue({ id: 7, email: 'karim@example.com', role: 'BRAND' })
  render(
    <MemoryRouter>
      <BrandRegistrationPage />
    </MemoryRouter>,
  )

  await user.type(screen.getByLabelText('Business name'), "Karim's Kitchen")
  await user.type(screen.getByLabelText('Contact person'), 'Karim')
  await user.type(screen.getByLabelText('Email'), 'karim@example.com')
  await user.type(screen.getByLabelText('Phone number'), '+961 1 234567')
  await user.type(screen.getByLabelText('Password'), 'correct horse')
  await user.type(screen.getByLabelText('Branch name'), 'Hamra')
  await user.type(screen.getByLabelText('Location'), 'Beirut')
  await user.click(screen.getByRole('button', { name: 'Add another branch' }))
  await user.type(screen.getAllByLabelText('Branch name')[1], 'Jounieh')
  await user.type(screen.getAllByLabelText('Location')[1], 'Keserwan')
  await user.click(screen.getByRole('button', { name: 'Create brand account' }))

  expect(apiFetchMock).toHaveBeenCalledWith('/api/public/auth/register/brand', {
    method: 'POST',
    body: {
      businessName: "Karim's Kitchen",
      contactPerson: 'Karim',
      email: 'karim@example.com',
      phone: '+961 1 234567',
      password: 'correct horse',
      branches: [
        { name: 'Hamra', location: 'Beirut' },
        { name: 'Jounieh', location: 'Keserwan' },
      ],
    },
  })
  expect(
    await screen.findByText('Your brand account is ready. You can now log in.'),
  ).toBeInTheDocument()
})
