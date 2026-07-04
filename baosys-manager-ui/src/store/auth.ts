import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { getCurrentUser } from '@/api/user'
import type { CurrentUser, LoginRequest } from '@/types/auth'
import { syncCsrfTokenFromCookie } from '@/utils/authSession'

export const useAuthStore = defineStore('auth', () => {
  const loading = ref(false)
  const currentUser = ref<CurrentUser | null>(null)
  const isAuthenticated = computed(() => currentUser.value !== null)
  // 路由和页面初始化可能并发恢复会话，共享请求可避免重复访问 /users/me。
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
      .then(user => {
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
      // 即使网络注销失败也清理内存态，避免界面继续展示受保护内容。
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
