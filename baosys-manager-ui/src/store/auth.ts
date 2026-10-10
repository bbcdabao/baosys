import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { getCurrentUser } from '@/api/user'
import { loadSystemCatalog } from '@/composables/useSystemCatalog'
import type { CurrentUser, LoginRequest } from '@/types/auth'
import { syncCsrfTokenFromCookie } from '@/utils/authSession'

export const useAuthStore = defineStore('auth', () => {
  const loading = ref(false)
  const currentUser = ref<CurrentUser | null>(null)
  const isAuthenticated = computed(() => currentUser.value !== null)
  let restorePromise: Promise<CurrentUser> | null = null

  const updateSession = (user: CurrentUser): void => {
    currentUser.value = user
  }

  const clearSession = (): void => {
    currentUser.value = null
  }

  const restoreSession = async (): Promise<CurrentUser> => {
    if (currentUser.value) {
      return currentUser.value
    }
    if (restorePromise) {
      return restorePromise
    }
    restorePromise = getCurrentUser()
      .then(async user => {
        await loadSystemCatalog()
        updateSession(user)
        syncCsrfTokenFromCookie()
        return user
      })
      .catch(error => {
        clearSession()
        throw error
      })
      .finally(() => {
        restorePromise = null
      })
    return restorePromise
  }

  const login = async (payload: LoginRequest): Promise<void> => {
    loading.value = true
    try {
      clearSession()
      await loginApi(payload)
      syncCsrfTokenFromCookie()
    } finally {
      loading.value = false
    }
  }

  const logout = async (): Promise<void> => {
    try {
      await logoutApi()
    } finally {
      clearSession()
    }
  }

  return {
    loading,
    currentUser,
    isAuthenticated,
    restoreSession,
    clearSession,
    login,
    logout
  }
})
