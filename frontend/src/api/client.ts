/** The JSON error body Spring sends (RFC 9457 ProblemDetail). */
export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
}

/** Thrown for any non-2xx response. `status` tells the caller what happened (401 = not logged in, 403 = wrong role). */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
  }
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const part = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return part ? decodeURIComponent(part.slice(prefix.length)) : undefined
}

/**
 * fetch() for our own API. The SPA and the API share one origin, so the session cookie goes along
 * automatically. For anything that changes data, the server's CSRF protection requires the value of
 * the XSRF-TOKEN cookie to be echoed in the X-XSRF-TOKEN header (docs/Architecture.md §5).
 */
export async function apiFetch<T>(
  path: string,
  options: { method?: string; body?: unknown; headers?: Record<string, string> } = {},
): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (!SAFE_METHODS.has(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    // An error body may be missing or not JSON (e.g. a proxy error page); the status still tells the story.
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
