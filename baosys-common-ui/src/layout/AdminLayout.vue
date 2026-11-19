<template>
  <AppSpaLayout>
    <template #sidebar>
      <Sidebar
        v-model:collapsed="collapsed"
        :app-name="appName"
        :app-short-name="appShortName"
        :items="items"
        :expand-label="expandLabel"
        :collapse-label="collapseLabel"
        :selected-keys="selectedKeys"
        :default-open-keys="defaultOpenKeys"
        :is-dark="isDark"
        @navigate="emit('navigate', $event)"
      >
        <template
          v-if="brandIcon"
          #brand
        >
          <component
            :is="brandIcon"
            class="brand-icon"
          /><span class="brand-title">{{ appName }}</span>
        </template>
      </Sidebar>
    </template>
    <template #header>
      <Header
        :locale="locale"
        :locale-options="localeOptions"
        :language-label="languageLabel"
        :is-dark="isDark"
        :user-name="userName"
        :logging-out="loggingOut"
        :labels="headerLabels"
        :user-action-label="userActionLabel"
        @user-click="emit('userClick')"
        @update:locale="emit('update:locale', $event)"
        @toggle-theme="emit('toggleTheme')"
        @logout="emit('logout')"
        @fullscreen-error="emit('fullscreenError')"
      >
        <Breadcrumb :items="breadcrumbs" />
      </Header>
    </template>
    <slot />
  </AppSpaLayout>
</template>
<script setup lang="ts">
import type { Component } from 'vue'
import AppSpaLayout from './AppSpaLayout.vue'
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
import Breadcrumb from './Breadcrumb.vue'
import type { SidebarItem, BreadcrumbItem, HeaderLabels, LocaleOption } from './types'
defineProps<{
  appName: string
  appShortName: string
  brandIcon?: Component
  items: SidebarItem[]
  expandLabel: string
  collapseLabel: string
  selectedKeys?: string[]
  defaultOpenKeys?: string[]
  breadcrumbs: BreadcrumbItem[]
  locale: string
  localeOptions: LocaleOption[]
  languageLabel?: string
  isDark: boolean
  userName?: string
  loggingOut?: boolean
  headerLabels: HeaderLabels
  userActionLabel: string
}>()
const collapsed = defineModel<boolean>('collapsed', { default: false })
const emit = defineEmits<{
  navigate: [key: string]
  'update:locale': [value: string]
  toggleTheme: []
  logout: []
  userClick: []
  fullscreenError: []
}>()
</script>
<style scoped>
.brand-icon {
  min-width: 28px;
  font-size: 26px;
}
.brand-title {
  white-space: nowrap;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-1);
}
</style>
