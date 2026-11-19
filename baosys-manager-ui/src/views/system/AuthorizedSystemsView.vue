<template>
  <AuthorizedSystems
    :systems="systemOptions"
    :permissions="auth.currentUser?.permissions ?? []"
    :permission-label="permissionLabel"
    :labels="labels"
  />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AuthorizedSystems } from '@baosys/common-ui/pages'
import { useSystemCatalog } from '@/composables/useSystemCatalog'
import { useAuthStore } from '@/store/auth'
const { t } = useI18n()
const { systemOptions, permissionLabel } = useSystemCatalog()
const auth = useAuthStore()
const labels = computed(() => ({
  title: t('user.authorizedSystems'),
  empty: t('user.noSystemAccess'),
  entryOnly: t('user.entryOnly'),
  hint: t('user.systemAccessHint')
}))
</script>
