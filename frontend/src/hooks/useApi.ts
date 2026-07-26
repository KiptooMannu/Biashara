import { useCallback, useEffect, useState } from 'react'
import { api, errorMessage } from '@/lib/api'

interface UseApiResult<T> {
  data: T | null
  loading: boolean
  error: string | null
  reload: () => Promise<void>
}

/**
 * Fetches a single endpoint and tracks its request state.
 *
 * Deliberately small: the alternative was a query library, and for a read-heavy
 * demo the caching it buys is not worth the dependency. Params are serialised into
 * the effect key so a filter change refetches without an explicit trigger.
 */
export function useApi<T>(
  url: string,
  params?: Record<string, unknown>,
  /**
   * Set false to skip the request. Exists so a permission-gated panel can decide
   * not to fetch without calling this hook conditionally, which would break the
   * rules of hooks.
   */
  enabled = true,
): UseApiResult<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)

  const key = params ? JSON.stringify(params) : ''

  const load = useCallback(async () => {
    if (!enabled) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const { data: response } = await api.get<T>(url, { params })
      setData(response)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, key, enabled])

  useEffect(() => {
    void load()
  }, [load])

  return { data, loading, error, reload: load }
}

/** Paged responses share one shape across every module. */
export interface Paged<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function usePagedApi<T>(url: string, extraParams?: Record<string, unknown>) {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  // Debounced so typing does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search)
      setPage(0)
    }, 350)
    return () => clearTimeout(timer)
  }, [search])

  const params: Record<string, unknown> = { page, size: 25, ...extraParams }
  if (debouncedSearch.trim()) {
    params.search = debouncedSearch.trim()
  }

  const result = useApi<Paged<T>>(url, params)

  return {
    ...result,
    rows: result.data?.content ?? [],
    page,
    setPage,
    totalPages: result.data?.totalPages ?? 0,
    totalElements: result.data?.totalElements ?? 0,
    search,
    setSearch,
  }
}
