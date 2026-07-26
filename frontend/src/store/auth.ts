import { create } from 'zustand'
import { api, tokenStore } from '@/lib/api'
import type { LoginResponse, UserSummary } from '@/lib/types'

interface AuthState {
  user: UserSummary | null
  /** True while the stored token is being exchanged for the current user. */
  bootstrapping: boolean
  mustChangePassword: boolean

  login: (email: string, password: string) => Promise<LoginResponse>
  logout: () => Promise<void>
  bootstrap: () => Promise<void>
  clearPasswordChangeFlag: () => void

  /** Permission check. The server enforces the same rule on every request. */
  can: (permission: string) => boolean
  canAny: (...permissions: string[]) => boolean
  hasRole: (role: string) => boolean
}

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  bootstrapping: true,
  mustChangePassword: false,

  async login(email, password) {
    const { data } = await api.post<LoginResponse>('/auth/login', { email, password })
    tokenStore.set(data.accessToken, data.refreshToken)
    set({ user: data.user, mustChangePassword: data.mustChangePassword, bootstrapping: false })
    return data
  },

  async logout() {
    try {
      await api.post('/auth/logout', null, { params: { refreshToken: tokenStore.refresh() } })
    } catch {
      // A failed revoke must not trap the user in a signed-in shell.
    }
    tokenStore.clear()
    set({ user: null, mustChangePassword: false })
  },

  /** Restores the session on a page reload from the persisted token. */
  async bootstrap() {
    if (!tokenStore.access()) {
      set({ user: null, bootstrapping: false })
      return
    }
    try {
      const { data } = await api.get<UserSummary>('/auth/me')
      set({ user: data, bootstrapping: false })
    } catch {
      tokenStore.clear()
      set({ user: null, bootstrapping: false })
    }
  },

  clearPasswordChangeFlag() {
    set({ mustChangePassword: false })
  },

  can(permission) {
    const user = get().user
    if (!user) return false
    return user.permissions.includes(permission)
  },

  canAny(...permissions) {
    const user = get().user
    if (!user) return false
    return permissions.some((permission) => user.permissions.includes(permission))
  },

  hasRole(role) {
    return get().user?.roles.includes(role) ?? false
  },
}))
