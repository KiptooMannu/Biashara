import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiErrorBody, LoginResponse } from './types'

const ACCESS_TOKEN_KEY = 'biashara.accessToken'
const REFRESH_TOKEN_KEY = 'biashara.refreshToken'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_TOKEN_KEY),
  refresh: () => localStorage.getItem(REFRESH_TOKEN_KEY),
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.access()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * Refreshes the access token once on a 401 and replays the original request.
 *
 * Concurrent 401s share a single refresh promise, so a dashboard firing eight
 * requests at once does not trigger eight refreshes and invalidate its own
 * rotated token.
 */
let refreshInFlight: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.refresh()
  if (!refreshToken) {
    throw new Error('No refresh token')
  }
  const { data } = await axios.post<LoginResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
  tokenStore.set(data.accessToken, data.refreshToken)
  return data.accessToken
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean }
    const status = error.response?.status
    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh')

    if (status === 401 && original && !original._retried && !isAuthCall && tokenStore.refresh()) {
      original._retried = true
      try {
        refreshInFlight = refreshInFlight ?? refreshAccessToken()
        const token = await refreshInFlight
        refreshInFlight = null
        original.headers.Authorization = `Bearer ${token}`
        return api.request(original)
      } catch {
        refreshInFlight = null
        tokenStore.clear()
        // Full reload so every store resets to a clean signed-out state.
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  },
)

/** Extracts a message worth showing the user out of an axios failure. */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0]
      if (first) return first
    }
    if (body?.message) return body.message
    if (error.code === 'ERR_NETWORK') {
      return 'Cannot reach the server. Is the backend running on port 8080?'
    }
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}
