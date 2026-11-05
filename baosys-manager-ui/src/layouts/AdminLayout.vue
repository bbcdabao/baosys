<template>
  <a-layout class="admin-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      class="admin-sider"
      collapsible
      breakpoint="lg"
      :width="232"
      :collapsed-width="64"
    >
      <div class="brand">
        <span class="brand-mark" :aria-label="t('app.name')">{{ t('app.shortName') }}</span>
        <span
          v-if="!collapsed"
          class="brand-name"
          >{{ t('app.name') }}</span
        >
      </div>

      <a-menu
        :theme="isDark ? 'dark' : 'light'"
        :selected-keys="selectedKeys"
        :default-open-keys="['user', 'system']"
        @menu-item-click="handleMenuClick"
      >
        <a-menu-item key="/authorized-systems">{{ t('user.authorizedSystems') }}</a-menu-item>
        <a-sub-menu
          v-if="can('MGR:user:list') || can('MGR:user:session:list')"
          key="user"
        >
          <template #icon><icon-user-group /></template>
          <template #title>{{ t('menu.user') }}</template>
          <a-menu-item
            v-if="can('MGR:user:list')"
            key="/users-management"
          >
            <template #icon><icon-user /></template>
            {{ t('menu.userManagement') }}
          </a-menu-item>
          <a-menu-item
            v-if="can('MGR:user:session:list')"
            key="/login-sessions"
          >
            <template #icon><icon-desktop /></template>
            {{ t('menu.loginManagement') }}
          </a-menu-item>
        </a-sub-menu>
        <a-sub-menu
          v-if="can('MGR')"
          key="system"
        >
          <template #icon><icon-settings /></template>
          <template #title>{{ t('menu.system') }}</template>
          <a-menu-item key="/system-config">
            <template #icon><icon-tool /></template>
            {{ t('menu.systemConfig') }}
          </a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>

    <a-layout class="admin-main">
      <a-layout-header class="admin-header">
        <div>
          <a-breadcrumb>
            <a-breadcrumb-item>{{ t('app.name') }}</a-breadcrumb-item>
            <a-breadcrumb-item>{{ menuTitle }}</a-breadcrumb-item>
            <a-breadcrumb-item>{{ pageTitle }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <a-space>
          <a-select
            v-model="locale"
            size="small"
            class="locale-select"
          >
            <a-option value="zh-CN">中文</a-option>
            <a-option value="en-US">English</a-option>
          </a-select>
          <a-tooltip :content="t(isDark ? 'common.light' : 'common.dark')">
            <a-button
              shape="circle"
              @click="toggleTheme"
            >
              <icon-sun-fill v-if="isDark" />
              <icon-moon-fill v-else />
            </a-button>
          </a-tooltip>
          <div class="user-chip">
            <icon-user />
            <span>{{ authStore.currentUser?.displayName }}</span>
          </div>
          <a-button
            type="text"
            :loading="loggingOut"
            @click="logout"
          >
            <template #icon><icon-export /></template>
            {{ t('common.logout') }}
          </a-button>
        </a-space>
      </a-layout-header>
      <a-layout-content class="admin-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const { locale } = useLocale()
const { isDark, toggleTheme } = useTheme()
const collapsed = ref(false)
const can = (permission: string): boolean => {
  const grants = authStore.currentUser?.permissions || []
  return grants.includes('MGR') && grants.includes(permission)
}

const selectedKeys = computed(() => [route.path])
const menuTitle = computed(() => t(String(route.meta.menuTitleKey || 'app.name')))
const pageTitle = computed(() => t(String(route.meta.titleKey || 'app.name')))

const handleMenuClick = (key: string): void => {
  // 菜单导航不携带 query，主动进入登录管理时自然清除用户筛选。
  void router.push(key)
}

const loggingOut = ref(false)
const logout = async (): Promise<void> => {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '退出失败，请重试')
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: var(--color-fill-2);
}
.admin-sider {
  border-right: 1px solid var(--color-border);
}
.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  overflow: hidden;
  color: rgb(var(--primary-6));
}
.brand-mark {
  display: grid;
  place-items: center;
  min-width: 28px;
  font-size: 26px;
  font-weight: 700;
}
.brand-name {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  color: var(--color-text-1);
}
.admin-main {
  min-width: 0;
}
.admin-header {
  height: 72px;
  padding: 10px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);
}
.admin-header h1 {
  margin: 4px 0 0;
  font-size: 20px;
  color: var(--color-text-1);
}
.locale-select {
  width: 104px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-2);
}
.admin-content {
  padding: 24px;
  overflow: auto;
}
@media (max-width: 768px) {
  .admin-header {
    padding: 10px 14px;
  }
  .admin-header h1,
  .user-chip {
    display: none;
  }
  .admin-content {
    padding: 12px;
  }
}
</style>
