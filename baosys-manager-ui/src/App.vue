<template>
  <a-config-provider :locale="arcoLocale">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { onUnmounted } from 'vue'

import { useLocale } from '@/composables/useLocale'
import emitter from '@/event-bus'
import { router } from '@/router'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const { arcoLocale } = useLocale()

const goLogin = (): void => {
  authStore.clearSession()
  void router.replace('/login')
}

emitter.on('go-login', goLogin)
onUnmounted(() => emitter.off('go-login', goLogin))
</script>
