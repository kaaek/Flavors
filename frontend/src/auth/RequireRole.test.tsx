import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from '../api/client'
import { AuthProvider } from './AuthProvider'
import { RequireRole } from './RequireRole'

// The server decides who is logged in; the tests fake its answer to GET /api/me.
vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  apiFetch: vi.fn(),
}))
const apiFetchMock = vi.mocked(apiFetch)

function renderBrandArea() {
  render(
    <MemoryRouter initialEntries={['/brand']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route
            path="/brand"
            element={
              <RequireRole role="BRAND">
                <p>brand dashboard</p>
              </RequireRole>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  // Braces matter: a function returned from beforeEach is run as cleanup, and the mock is a function.
  apiFetchMock.mockReset()
})

describe('RequireRole', () => {
  it('shows a loading message until the server answers', () => {
    apiFetchMock.mockReturnValue(new Promise(() => {}))
    renderBrandArea()

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('shows the area to a user with the right role', async () => {
    apiFetchMock.mockResolvedValue({ id: 1, email: 'b@example.com', role: 'BRAND' })
    renderBrandArea()

    expect(await screen.findByText('brand dashboard')).toBeInTheDocument()
  })

  it('redirects to the login page on 401', async () => {
    apiFetchMock.mockRejectedValue(new ApiError(401, {}))
    renderBrandArea()

    expect(await screen.findByText('login page')).toBeInTheDocument()
  })

  it('says "Not allowed" for the wrong role', async () => {
    apiFetchMock.mockResolvedValue({ id: 2, email: 'c@example.com', role: 'CREATOR' })
    renderBrandArea()

    expect(await screen.findByText('Not allowed')).toBeInTheDocument()
    expect(screen.queryByText('brand dashboard')).not.toBeInTheDocument()
  })

  it('does not treat a server failure as being logged out', async () => {
    apiFetchMock.mockRejectedValue(new ApiError(500, {}))
    renderBrandArea()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
  })
})
