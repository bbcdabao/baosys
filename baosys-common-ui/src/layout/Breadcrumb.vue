<template>
  <a-breadcrumb>
    <a-breadcrumb-item
      v-for="item in items"
      :key="item.key"
      >{{ item.label }}</a-breadcrumb-item
    >
  </a-breadcrumb>
</template>

<script setup lang="ts">
import { Breadcrumb as ABreadcrumb, BreadcrumbItem as ABreadcrumbItem } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/breadcrumb/style/css.js'
import type { BreadcrumbItem } from './types'
defineProps<{ items: BreadcrumbItem[] }>()
</script>
