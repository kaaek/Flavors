import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'

type Result<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: T }

/** Loads one API path when the page opens. The role homes use it for their small summaries. */
export function useFetch<T>(path: string): Result<T> {
  const [result, setResult] = useState<Result<T>>({ status: 'loading' })
  useEffect(() => {
    let cancelled = false
    apiFetch<T>(path)
      .then((data) => !cancelled && setResult({ status: 'ready', data }))
      .catch(() => !cancelled && setResult({ status: 'error' }))
    return () => {
      cancelled = true
    }
  }, [path])
  return result
}
