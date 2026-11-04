import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { logout as logoutApi } from '@/api/auth'
import { getCurrentUser } from '@/api/user'
import { loadSystemCatalog } from '@/composables/useSystemCatalog'
import type { CurrentUser } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUser | null>(null)
  const isAuthenticated = computed(() => currentUser.value !== null)
  // 并发恢复会话共用一次请求，避免重复加载用户和系统目录。
  let restorePromise: Promise<CurrentUser> | null = null
  // 每次清理会话递增版本，防止先前请求完成后覆盖新的登录状态。
  let generation = 0

  const updateSession = (user: CurrentUser): void => {
    currentUser.value = user
  }

  const clearSession = (): void => {
    generation++
    restorePromise = null
    currentUser.value = null
  }

  const restoreSession = async (): Promise<CurrentUser> => {
    if (currentUser.value) {
      return currentUser.value
    }
    if (restorePromise) {
      return restorePromise
    }
    const started = generation
    restorePromise = getCurrentUser()
      .then(async (user) => {
        await loadSystemCatalog()
        if (started !== generation) {
          throw new Error('登录状态已变更')
        }
        updateSession(user)
        return user
      })
      .catch((error) => {
        if (started === generation) {
          currentUser.value = null
        }
        throw error
      })
      .finally(() => {
        if (started === generation) {
          restorePromise = null
        }
      })
    return restorePromise
  }

  const logout = async (): Promise<void> => {
    await logoutApi()
    clearSession()
  }

  return {
    currentUser,
    isAuthenticated,
    restoreSession,
    clearSession,
    logout
  }
})
