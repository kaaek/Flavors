import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { PortfolioPage } from './PortfolioPage'
import type { Portfolio } from './portfolioApi'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

beforeEach(() => {
  // Braces on purpose: Vitest runs a function returned from beforeEach as a cleanup, and mockReset() returns the mock.
  apiFetchMock.mockReset()
})

const GET = 'GET /api/creator/portfolio'
const PUT = 'PUT /api/creator/portfolio'

const portfolio: Portfolio = {
  displayName: 'Lara',
  bio: 'I love burgers',
  foodCategory: 'Burgers',
  location: 'Beirut',
  priceMinCents: 2500,
  priceMaxCents: 10050,
  socialAccounts: [
    {
      platform: 'instagram',
      status: 'CONNECTED',
      followers: 12000,
      engagementRate: 4.25,
      lastPulledAt: '2026-10-30T10:00:00Z',
    },
  ],
  completedCampaigns: [
    { id: 42, name: 'Summer burgers', featured: true },
    { id: 43, name: 'Winter desserts', featured: false },
  ],
}

/** Answers each request by "METHOD path"; a handler that throws makes that request fail. */
function serve(routes: Record<string, () => unknown>) {
  apiFetchMock.mockImplementation(async (path, options) => {
    const key = `${options?.method ?? 'GET'} ${path}`
    const handler = routes[key]
    if (!handler) throw new Error(`Unexpected request: ${key}`)
    return handler()
  })
}

function refused(status: number, title: string, detail: string) {
  return () => {
    throw new ApiError(status, { title, status, detail })
  }
}

describe('loading', () => {
  it('shows her details, accounts and completed campaigns', async () => {
    serve({ [GET]: () => portfolio })

    render(<PortfolioPage />)

    expect(await screen.findByLabelText('Category')).toHaveValue('Burgers')
    expect(screen.getByLabelText('Bio')).toHaveValue('I love burgers')
    expect(screen.getByLabelText('Location')).toHaveValue('Beirut')
    expect(screen.getByLabelText('Minimum price (USD)')).toHaveValue('25.00')
    expect(screen.getByLabelText('Maximum price (USD)')).toHaveValue('100.50')
    expect(screen.getByText('instagram')).toBeInTheDocument()
    expect(screen.getByText(/12,000 followers/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Summer burgers' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Winter desserts' })).not.toBeChecked()
  })

  it('shows a reconnection warning for an account that needs it', async () => {
    const needsReconnection = {
      ...portfolio,
      socialAccounts: [{ ...portfolio.socialAccounts[0], status: 'NEEDS_RECONNECTION' as const }],
    }
    serve({ [GET]: () => needsReconnection })

    render(<PortfolioPage />)

    expect(await screen.findByText('Needs reconnection')).toBeInTheDocument()
  })

  it('says so when she has no accounts and no completed campaigns', async () => {
    serve({ [GET]: () => ({ ...portfolio, socialAccounts: [], completedCampaigns: [] }) })

    render(<PortfolioPage />)

    expect(await screen.findByText('No social account is connected yet.')).toBeInTheDocument()
    expect(screen.getByText(/You have no completed campaigns yet/)).toBeInTheDocument()
  })

  it('explains that an unapproved creator has no portfolio yet', async () => {
    serve({
      [GET]: refused(
        409,
        'Portfolio not available',
        'Your portfolio is available after an admin approves your profile.',
      ),
    })

    render(<PortfolioPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('available after an admin approves')
    expect(screen.queryByLabelText('Bio')).not.toBeInTheDocument()
  })

  it('shows a message when the portfolio cannot be loaded', async () => {
    serve({
      [GET]: () => {
        throw new Error('network down')
      },
    })

    render(<PortfolioPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your portfolio.')
  })
})

describe('saving the details', () => {
  it('sends a full replace with the prices in cents and shows success', async () => {
    const user = userEvent.setup()
    serve({ [GET]: () => portfolio, [PUT]: () => ({ ...portfolio, bio: 'New bio' }) })
    render(<PortfolioPage />)

    const bio = await screen.findByLabelText('Bio')
    await user.clear(bio)
    await user.type(bio, 'New bio')
    const min = screen.getByLabelText('Minimum price (USD)')
    await user.clear(min)
    await user.type(min, '30')
    const max = screen.getByLabelText('Maximum price (USD)')
    await user.clear(max)
    await user.type(max, '120.5')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Portfolio saved.')
    expect(apiFetchMock).toHaveBeenLastCalledWith('/api/creator/portfolio', {
      method: 'PUT',
      body: {
        bio: 'New bio',
        foodCategory: 'Burgers',
        location: 'Beirut',
        priceMinCents: 3000,
        priceMaxCents: 12050,
      },
    })
  })

  it('sends an empty bio as null', async () => {
    const user = userEvent.setup()
    serve({ [GET]: () => portfolio, [PUT]: () => ({ ...portfolio, bio: null }) })
    render(<PortfolioPage />)

    await user.clear(await screen.findByLabelText('Bio'))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await screen.findByRole('status')
    expect(apiFetchMock).toHaveBeenLastCalledWith(
      '/api/creator/portfolio',
      expect.objectContaining({ body: expect.objectContaining({ bio: null }) }),
    )
  })

  it("shows the server's message when the edit is refused", async () => {
    const user = userEvent.setup()
    serve({
      [GET]: () => portfolio,
      [PUT]: refused(400, 'Invalid portfolio', "Minimum price can't be above the maximum price."),
    })
    render(<PortfolioPage />)

    await user.click(await screen.findByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Minimum price can't be above the maximum price.",
    )
  })

  it('does not send anything when a price is not an amount', async () => {
    const user = userEvent.setup()
    serve({ [GET]: () => portfolio })
    render(<PortfolioPage />)

    const min = await screen.findByLabelText('Minimum price (USD)')
    await user.clear(min)
    await user.type(min, 'abc')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the prices in dollars')
    expect(apiFetchMock).toHaveBeenCalledTimes(1) // only the GET
  })
})

describe('featured campaigns', () => {
  it('features a campaign when its box is ticked', async () => {
    const user = userEvent.setup()
    serve({
      [GET]: () => portfolio,
      'POST /api/creator/portfolio/featured': () => ({
        id: 43,
        name: 'Winter desserts',
        featured: true,
      }),
    })
    render(<PortfolioPage />)

    await user.click(await screen.findByRole('checkbox', { name: 'Winter desserts' }))

    expect(apiFetchMock).toHaveBeenLastCalledWith('/api/creator/portfolio/featured', {
      method: 'POST',
      body: { campaignId: 43 },
    })
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Winter desserts' })).toBeChecked(),
    )
  })

  it('stops featuring a campaign when its box is unticked', async () => {
    const user = userEvent.setup()
    serve({ [GET]: () => portfolio, 'DELETE /api/creator/portfolio/featured/42': () => undefined })
    render(<PortfolioPage />)

    await user.click(await screen.findByRole('checkbox', { name: 'Summer burgers' }))

    expect(apiFetchMock).toHaveBeenLastCalledWith('/api/creator/portfolio/featured/42', {
      method: 'DELETE',
    })
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Summer burgers' })).not.toBeChecked(),
    )
  })

  it('treats "already featured" as featured, without an error', async () => {
    const user = userEvent.setup()
    serve({
      [GET]: () => portfolio,
      'POST /api/creator/portfolio/featured': refused(
        409,
        'Already featured',
        'This campaign is already featured on your portfolio.',
      ),
    })
    render(<PortfolioPage />)

    await user.click(await screen.findByRole('checkbox', { name: 'Winter desserts' }))

    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Winter desserts' })).toBeChecked(),
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the message and leaves the box alone when featuring is refused', async () => {
    const user = userEvent.setup()
    serve({
      [GET]: () => portfolio,
      'POST /api/creator/portfolio/featured': refused(
        400,
        'Invalid portfolio',
        'Only your completed campaigns can be featured.',
      ),
    })
    render(<PortfolioPage />)

    await user.click(await screen.findByRole('checkbox', { name: 'Winter desserts' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only your completed campaigns')
    expect(screen.getByRole('checkbox', { name: 'Winter desserts' })).not.toBeChecked()
  })
})
