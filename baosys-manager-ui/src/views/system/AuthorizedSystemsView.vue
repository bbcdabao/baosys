<template>
  <a-card
    :title="t('user.authorizedSystems')"
    :bordered="false"
  >
    <a-empty
      v-if="authorized.length === 0"
      :description="t('user.noSystemAccess')"
    />
    <a-space
      v-else
      wrap
    >
      <a-button
        v-for="system in authorized"
        :key="system.value"
        type="primary"
        :href="system.homeUrl"
        >{{ system.label }}</a-button
      >
    </a-space>
    <p>{{ t('user.systemAccessHint') }}</p>
  </a-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSystemCatalog } from '@/composables/useSystemCatalog'
import { useAuthStore } from '@/store/auth'
const { t } = useI18n()
const { systemOptions } = useSystemCatalog()
const auth = useAuthStore()
const authorized = computed(() =>
  systemOptions.value.filter((system) => auth.currentUser?.permissions.includes(system.value))
)
</script>
