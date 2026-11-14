import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { SimulatedCheckoutPage } from './SimulatedCheckoutPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

function Where() {
  return <p data-testid="where">{useLocation().pathname + useLocation().search}</p>
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/payments/simulated/ref-1']}>
      <Routes>
        <Route path="/payments/simulated/:reference" element={<SimulatedCheckoutPage />} />
        <Route path="/brand/campaigns/:id" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

const checkout = {
  campaignId: 42,
  campaignName: 'Summer menu launch',
  amountCents: 65000,
  currency: 'USD',
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

it('shows the campaign and the amount without asking for card details', async () => {
  apiFetchMock.mockResolvedValue({ ...checkout, status: 'PENDING' })
  renderPage()

  expect(await screen.findByText('Summer menu launch')).toBeInTheDocument()
  expect(screen.getByText('$650.00')).toBeInTheDocument()
  expect(screen.queryByLabelText(/card number/i)).not.toBeInTheDocument()
})

it('pays and returns the brand to the campaign with the result held', async () => {
  apiFetchMock
    .mockResolvedValueOnce({ ...checkout, status: 'PENDING' })
    .mockResolvedValueOnce({ ...checkout, status: 'SUCCEEDED' })
  renderPage()

  fireEvent.click(await screen.findByRole('button', { name: 'Pay with test card' }))

  expect(await screen.findByTestId('where')).toHaveTextContent('/brand/campaigns/42?payment=held')
  expect(apiFetchMock.mock.calls[1][0]).toBe('/api/public/payments/simulated/ref-1/complete')
})

it('a declined card returns the brand to the campaign with the payment failed', async () => {
  apiFetchMock
    .mockResolvedValueOnce({ ...checkout, status: 'PENDING' })
    .mockResolvedValueOnce({ ...checkout, status: 'FAILED' })
  renderPage()

  fireEvent.click(await screen.findByRole('button', { name: 'Simulate a declined card' }))

  await waitFor(() =>
    expect(screen.getByTestId('where')).toHaveTextContent('/brand/campaigns/42?payment=failed'),
  )
})
