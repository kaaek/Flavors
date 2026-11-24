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

/** Like {@link useFetch}, for a call that is not a plain GET of a path (e.g. one of the typed functions in api/). */
export function useFetchWith<T>(load: () => Promise<T>): Result<T> {
  const [result, setResult] = useState<Result<T>>({ status: 'loading' })
  useEffect(() => {
    let cancelled = false
    load()
      .then((data) => !cancelled && setResult({ status: 'ready', data }))
      .catch(() => !cancelled && setResult({ status: 'error' }))
    return () => {
      cancelled = true
    }
  }, [load])
  return result
}
