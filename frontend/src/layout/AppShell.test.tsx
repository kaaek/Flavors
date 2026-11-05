import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { apiFetch } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import type { Role } from '../auth/authContext'
import { navItems } from './navItems'

// The server decides who is logged in and what the pages show; the tests fake its answers.
vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

/** Logged in as this role; every other API call answers with what `answers` says for its path, else fails. */
function serverWith(role: Role, answers: Record<string, unknown> = {}) {
  apiFetchMock.mockImplementation((path: string) => {
    if (path === '/api/me') return Promise.resolve({ id: 1, email: 'who@example.com', role })
    if (path in answers) return Promise.resolve(answers[path])
    return Promise.reject(new Error(`no answer for ${path}`))
  })
}

function openAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

describe('the top bar', () => {
  it.each(['CREATOR', 'BRAND', 'ADMIN'] as const)(
    'lists exactly the pages of a %s',
    async (role) => {
      serverWith(role)
      openAt(navItems[role][0].to)

      const bar = await screen.findByRole('navigation', { name: 'Main' })
      const labels = within(bar)
        .getAllByRole('link')
        .map((link) => link.textContent)
      // The logo (named "Flavors home") comes first, then one link per nav item.
      expect(labels.slice(1).map((l) => l?.replace(/[\d\s]+$/, ''))).toEqual(
        navItems[role].map((i) => i.label),
      )
      expect(within(bar).getByRole('button', { name: 'Log out' })).toBeInTheDocument()
      expect(within(bar).getByText('who@example.com')).toBeInTheDocument()
    },
  )

  it('marks the page you are on as current, and only that one', async () => {
    serverWith('CREATOR')
    openAt('/creator/portfolio')

    const bar = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(bar).getByRole('link', { name: 'Portfolio' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(within(bar).getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
  })

  it('takes you from one page to another without typing an address', async () => {
    const user = userEvent.setup()
    serverWith('CREATOR')
    openAt('/creator')

    const bar = await screen.findByRole('navigation', { name: 'Main' })
    await user.click(within(bar).getByRole('link', { name: 'Social accounts' }))

    expect(await screen.findByRole('heading', { name: 'Social accounts' })).toBeInTheDocument()
    expect(within(bar).getByRole('link', { name: 'Social accounts' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('shows the admin how many creators wait', async () => {
    serverWith('ADMIN', { '/api/admin/creators/pending': [{ id: 1 }, { id: 2 }] })
    openAt('/admin')

    const bar = await screen.findByRole('navigation', { name: 'Main' })
    expect(await within(bar).findByRole('link', { name: 'Approvals 2' })).toBeInTheDocument()
  })

  it('logs out and returns to the login page', async () => {
    const user = userEvent.setup()
    serverWith('BRAND')
    openAt('/brand')
    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    await waitFor(() =>
      expect(apiFetchMock).toHaveBeenCalledWith('/api/auth/logout', { method: 'POST' }),
    )
  })
})

describe('areas and unknown pages', () => {
  it('sends a user of another role home instead of leaving her at a dead end', async () => {
    serverWith('BRAND')
    openAt('/admin')

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to my home' })).toHaveAttribute('href', '/brand')
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
  })

  it('shows "Page not found" inside the bar for an unknown address in her own area', async () => {
    serverWith('CREATOR')
    openAt('/creator/nothing-here')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to my home' })).toHaveAttribute('href', '/creator')
  })

  it('sends a logged-in user from the start page to her own home', async () => {
    serverWith('ADMIN')
    openAt('/')

    expect(await screen.findByRole('heading', { name: 'Admin console' })).toBeInTheDocument()
  })
})
