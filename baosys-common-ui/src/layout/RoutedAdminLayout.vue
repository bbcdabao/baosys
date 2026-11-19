<template>
  <AdminLayout
    v-model:collapsed="collapsed"
    :app-name="t('app.name')"
    :app-short-name="appShortName"
    :brand-icon="brandIcon"
    :items="items"
    :default-open-keys="openGroups"
    :selected-keys="[route.path]"
    :breadcrumbs="breadcrumbs"
    :expand-label="t('common.expandSidebar')"
    :collapse-label="t('common.collapseSidebar')"
    :locale="locale"
    :locale-options="localeOptions"
    :language-label="t('common.language')"
    :is-dark="isDark"
    :header-labels="headerLabels"
    :user-name="userName"
    :user-action-label="t('user.authorizedSystems')"
    :logging-out="loggingOut"
    @navigate="navigate"
    @update:locale="updateLocale"
    @toggle-theme="toggleTheme"
    @user-click="navigate('/authorized-systems')"
    @logout="logout"
    @fullscreen-error="Message.warning(t('common.fullscreenUnavailable'))"
  >
    <slot><router-view :key="remountOnQuery ? route.fullPath : undefined" /></slot>
  </AdminLayout>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/message/style/css.js'
import AdminLayout from './AdminLayout.vue'
import { createRouteMenus } from './navigation'
import { createBreadcrumbs } from './breadcrumbs'
import { useLocale, localeOptions, isAppLocale } from '@baosys/common-ui/i18n'
// Use the same exported entry as business pages so Vite shares preference state.
import { useTheme } from '@baosys/common-ui/preferences'
const props = withDefaults(
  defineProps<{
    appShortName?: string
    brandIcon?: Component
    userName?: string
    can: (permission: string) => boolean
    systemPermission: string
    signOut: () => void | Promise<void>
    remountOnQuery?: boolean
  }>(),
  { appShortName: '' }
)
const route = useRoute(),
  router = useRouter()
const { t } = useI18n()
const { locale } = useLocale()
const { isDark, toggleTheme } = useTheme()
const collapsed = ref(false),
  loggingOut = ref(false)
const items = computed(() =>
  props.can(props.systemPermission) ? createRouteMenus(router.getRoutes(), t, props.can) : []
)
const openGroups = computed(() =>
  items.value.filter((item) => item.children).map((item) => item.key)
)
const breadcrumbs = computed(() =>
  createBreadcrumbs(
    t('app.subsystemName'),
    route.matched
      .filter((record) => record.meta.titleKey)
      .map((record) => ({ key: record.path, label: t(record.meta.titleKey!) }))
  )
)
const headerLabels = computed(() => ({
  theme: t(isDark.value ? 'common.light' : 'common.dark'),
  fullscreen: t('common.fullscreen'),
  exitFullscreen: t('common.exitFullscreen'),
  logout: t('common.logout')
}))
const navigate = (path: string) => {
  void router.push(path)
}
const updateLocale = (value: string) => {
  if (isAppLocale(value)) locale.value = value
}
const logout = async () => {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await props.signOut()
  } catch {
    Message.error(t('signOut.navigationFailed'))
  } finally {
    loggingOut.value = false
  }
}
watch(collapsed, async () => {
  await nextTick()
  window.dispatchEvent(new Event('resize'))
})
</script>
