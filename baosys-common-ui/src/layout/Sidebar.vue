<template>
  <a-layout-sider
    v-model:collapsed="collapsed"
    class="admin-sider"
    collapsible
    hide-trigger
    breakpoint="lg"
    :style="{
      width: collapsed ? 'var(--baosys-sidebar-collapsed-width)' : 'var(--baosys-sidebar-width)'
    }"
  >
    <div class="sidebar-top">
      <a-button
        class="sidebar-toggle"
        type="text"
        :aria-label="collapsed ? expandLabel : collapseLabel"
        :title="collapsed ? expandLabel : collapseLabel"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        <template #icon><icon-menu-unfold v-if="collapsed" /><icon-menu-fold v-else /></template>
      </a-button>
      <div
        v-if="!collapsed"
        class="brand"
      >
        <slot
          name="brand"
          :collapsed="collapsed"
        >
          <span
            class="brand-mark"
            :aria-label="appName"
            >{{ appShortName }}</span
          >
          <span
            v-if="!collapsed"
            class="brand-name"
            >{{ appName }}</span
          >
        </slot>
      </div>
    </div>
    <a-menu
      :theme="isDark ? 'dark' : 'light'"
      class="sidebar-menu"
      :selected-keys="selectedKeys"
      :default-open-keys="defaultOpenKeys"
      @menu-item-click="(key: string) => emit('navigate', key)"
    >
      <SidebarMenuItem
        v-for="item in items"
        :key="item.key"
        :item="item"
      />
    </a-menu>
  </a-layout-sider>
</template>

<script setup lang="ts">
import { LayoutSider as ALayoutSider, Menu as AMenu, Button as AButton } from '@arco-design/web-vue'
import { IconMenuFold, IconMenuUnfold } from '@arco-design/web-vue/es/icon'
import '@arco-design/web-vue/es/button/style/css.js'
import '@arco-design/web-vue/es/layout/style/css.js'
import '@arco-design/web-vue/es/menu/style/css.js'
import SidebarMenuItem from './SidebarMenuItem.vue'
import type { SidebarItem } from './types'

withDefaults(
  defineProps<{
    appName: string
    appShortName: string
    expandLabel: string
    collapseLabel: string
    items: SidebarItem[]
    selectedKeys?: string[]
    defaultOpenKeys?: string[]
    isDark?: boolean
  }>(),
  { selectedKeys: () => [], defaultOpenKeys: () => [], isDark: false }
)
const collapsed = defineModel<boolean>('collapsed', { default: false })
const emit = defineEmits<{ navigate: [key: string] }>()
</script>

<style scoped>
.admin-sider {
  box-sizing: border-box;
  height: 100%;
  overflow: hidden;
  border-right: var(--baosys-border-width) solid var(--color-border);
}
.admin-sider :deep(.arco-layout-sider-children) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-top {
  display: flex;
  align-items: center;
  height: var(--app-measured-header-height, var(--baosys-header-height));
  flex-shrink: 0;
}
.sidebar-toggle {
  flex-shrink: 0;
  margin: 0 16px;
  width: 32px;
  height: 32px;
  color: var(--color-text-2);
}
.sidebar-menu {
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.sidebar-menu :deep(.arco-menu-inner) {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 4px 8px;
  overflow-y: auto;
  overflow-x: hidden;
}
.sidebar-menu :deep(.arco-menu-item),
.sidebar-menu :deep(.arco-menu-inline-header),
.sidebar-menu :deep(.arco-menu-pop-header) {
  box-sizing: border-box;
  min-height: var(--baosys-menu-item-height);
  padding-left: 16px;
  min-width: 0;
  overflow: hidden;
}
.sidebar-menu :deep(.arco-menu-indent-list) {
  display: none;
}
.sidebar-menu :deep(.arco-menu-icon) {
  width: var(--baosys-menu-icon-size);
  display: inline-flex;
  justify-content: center;
  flex-shrink: 0;
}
.sidebar-menu :deep(.arco-menu-title),
.sidebar-menu :deep(.arco-menu-item-inner) {
  min-width: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
  overflow: hidden;
}
.sidebar-menu.arco-menu-collapsed :deep(.arco-menu-has-icon > *:not(.arco-menu-icon)) {
  display: none;
}
.sidebar-menu.arco-menu-collapsed :deep(.arco-menu-item),
.sidebar-menu.arco-menu-collapsed :deep(.arco-menu-inline-header),
.sidebar-menu.arco-menu-collapsed :deep(.arco-menu-pop-header) {
  /* Collapsed items have no submenu arrow; leave room for the fixed gutter. */
  padding-right: 0;
}
.sidebar-menu.arco-menu-collapsed :deep(.arco-menu-icon),
.sidebar-menu.arco-menu-collapsed :deep(.arco-icon) {
  margin-right: 0;
}
.brand {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px 0 0;
  min-width: 0;
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
</style>
