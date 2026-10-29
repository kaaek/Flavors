import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from './client'

const fetchMock = vi.fn()

function respond(status: number, body?: unknown) {
  fetchMock.mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status }),
  )
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=abc%3D123'
})

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('apiFetch', () => {
  it('returns the parsed JSON and sends no CSRF header on GET', async () => {
    respond(200, { id: 1 })

    await expect(apiFetch('/api/me')).resolves.toEqual({ id: 1 })

    const init = fetchMock.mock.calls[0][1]
    expect(init.method).toBe('GET')
    expect(init.credentials).toBe('same-origin')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('echoes the XSRF-TOKEN cookie on POST and sends a JSON body', async () => {
    respond(200, {})

    await apiFetch('/api/brand/campaigns', { method: 'POST', body: { name: 'x' } })

    const init = fetchMock.mock.calls[0][1]
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc=123')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"name":"x"}')
  })

  it('turns a ProblemDetail response into an ApiError', async () => {
    respond(403, {
      title: 'Forbidden',
      status: 403,
      detail: 'Your account is not allowed to do this.',
    })

    const error = await apiFetch('/api/admin/x').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(403)
    expect((error as ApiError).message).toBe('Your account is not allowed to do this.')
  })

  it('still reports the status when the error body is not JSON', async () => {
    fetchMock.mockResolvedValue(new Response('<html>bad gateway</html>', { status: 502 }))

    const error = await apiFetch('/api/me').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(502)
  })

  it('returns undefined for 204 No Content', async () => {
    respond(204)

    await expect(apiFetch('/api/x', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
