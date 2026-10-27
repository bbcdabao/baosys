<template>
  <a-config-provider :locale="arcoLocale">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

import { useLocale } from '@/composables/useLocale'
import emitter from '@/event-bus'
import { router } from '@/router'
import { useAuthStore } from '@/store/auth'
import { goToManagerLogin } from '@/utils/loginRedirect'

const authStore = useAuthStore()
const { arcoLocale } = useLocale()

const goLogin = (): void => {
  authStore.clearSession()
  goToManagerLogin()
}

// Cookies are shared across ports; recheck identity when returning to an already-open Manager tab.
let checkingIdentity = false
const checkIdentity = async (): Promise<void> => {
  if (
    document.visibilityState !== 'visible' ||
    router.currentRoute.value.meta.public ||
    checkingIdentity
  ) {
    return
  }
  checkingIdentity = true
  const previous = authStore.currentUser?.userId
  authStore.clearSession()
  try {
    const user = await authStore.restoreSession()
    if (previous !== undefined && user.userId !== previous) {
      window.location.reload()
    }
  } catch {
    /* The request interceptor handles authentication failures. */
  } finally {
    checkingIdentity = false
  }
}
onMounted(() => document.addEventListener('visibilitychange', checkIdentity))

emitter.on('go-login', goLogin)
onUnmounted(() => {
  emitter.off('go-login', goLogin)
  document.removeEventListener('visibilitychange', checkIdentity)
})
</script>
