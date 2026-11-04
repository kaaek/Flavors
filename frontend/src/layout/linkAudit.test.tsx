import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { apiFetch } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import type { Role } from '../auth/authContext'
import { homeByRole } from '../auth/homeByRole'

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

/** Opens one address as this role and returns every in-app link on the screen, plus whether it was a 404. */
async function visit(path: string, role: Role) {
  apiFetchMock.mockImplementation((p: string) =>
    p === '/api/me'
      ? Promise.resolve({ id: 1, email: 'who@example.com', role })
      : Promise.reject(new Error(`no answer for ${p}`)),
  )
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  )
  await screen.findByRole('navigation', { name: 'Main' })
  const notFound = screen.queryByRole('heading', { name: 'Page not found' }) !== null
  const links = Array.from(document.querySelectorAll('a[href]'))
    .map((a) => a.getAttribute('href') ?? '')
    .filter((href) => href.startsWith('/'))
  cleanup()
  return { notFound, links }
}

beforeEach(() => {
  apiFetchMock.mockReset()
})

// The point of the top bar is that nobody types addresses: starting at a role's home and following every link on
// every page it reaches must never end on "Page not found", and must reach every page of that role's bar.
it.each(['CREATOR', 'BRAND', 'ADMIN'] as const)(
  'every link a %s can follow leads to a page that exists',
  async (role) => {
    const seen = new Set<string>()
    const queue = [homeByRole[role]]
    while (queue.length > 0) {
      const path = queue.shift()!
      if (seen.has(path)) continue
      seen.add(path)

      const { notFound, links } = await visit(path, role)
      expect(notFound, `${path} is a dead end`).toBe(false)
      // Stay in this role's area: other areas show "Not allowed", which is checked in AppShell.test.tsx.
      for (const href of links) {
        const area = href.split('/')[1]
        const own = homeByRole[role].slice(1)
        if (href === '/' || area === own) queue.push(href)
      }
    }

    const { navItems } = await import('./navItems')
    for (const item of navItems[role]) {
      expect(seen, `${item.label} is not reachable from home`).toContain(item.to)
    }
  },
)
