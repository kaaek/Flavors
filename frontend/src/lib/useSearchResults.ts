import { useEffect, useState } from 'react'

type Result<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: T }

/**
 * Runs `load(params)` whenever `params` changes (pass an already-debounced value). `load` must be a stable function
 * (a module-level API call). An answer is stored together with the params it belongs to, so while newer params are
 * in flight the result reads as "loading", and a slow earlier answer can never overwrite a newer one.
 */
export function useSearchResults<P, T>(params: P, load: (params: P) => Promise<T>): Result<T> {
  const [answer, setAnswer] = useState<{ params: P; result: Result<T> } | null>(null)
  useEffect(() => {
    let stale = false
    load(params)
      .then((data) => !stale && setAnswer({ params, result: { status: 'ready', data } }))
      .catch(() => !stale && setAnswer({ params, result: { status: 'error' } }))
    return () => {
      stale = true
    }
  }, [params, load])
  return answer !== null && answer.params === params ? answer.result : { status: 'loading' }
}
