<template>
  <a-sub-menu
    v-if="item.children?.length"
    :key="item.key"
  >
    <template
      v-if="item.icon"
      #icon
      ><component :is="item.icon"
    /></template>
    <template #title
      ><span :title="item.label">{{ item.label }}</span></template
    >
    <SidebarMenuItem
      v-for="child in item.children"
      :key="child.key"
      :item="child"
    />
  </a-sub-menu>
  <a-menu-item
    v-else
    :key="item.key"
  >
    <template
      v-if="item.icon"
      #icon
      ><component :is="item.icon"
    /></template>
    <span :title="item.label">{{ item.label }}</span>
  </a-menu-item>
</template>

<script setup lang="ts">
import { MenuItem as AMenuItem, SubMenu as ASubMenu } from '@arco-design/web-vue'
import type { SidebarItem } from './types'
defineProps<{ item: SidebarItem }>()
</script>
