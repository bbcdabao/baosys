<template>
  <a-layout
    ref="layout"
    class="admin-layout"
    :style="{
      '--app-measured-header-height': headerHeight === undefined ? undefined : `${headerHeight}px`
    }"
    :has-sider="Boolean($slots.sidebar)"
  >
    <slot name="sidebar" />
    <a-layout class="admin-main">
      <slot name="header" />
      <a-layout-content class="admin-content"><slot /></a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { Layout as ALayout, LayoutContent as ALayoutContent } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/layout/style/css.js'
import '../styles/main.css'

const layout = ref<InstanceType<typeof ALayout>>()
const headerHeight = ref<number>()
let observer: ResizeObserver | undefined
onMounted(() => {
  const header = (layout.value?.$el as HTMLElement).querySelector('.admin-header')
  if (!header) {
    headerHeight.value = 0
    return
  }
  const measure = () => {
    headerHeight.value = header.getBoundingClientRect().height
  }
  measure()
  observer = new ResizeObserver(measure)
  observer.observe(header)
})
onUnmounted(() => observer?.disconnect())
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-fill-2);
}
.admin-main {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.admin-content {
  min-height: 0;
  padding: var(--baosys-workspace-padding-top) var(--baosys-workspace-padding-right)
    var(--baosys-workspace-padding-bottom) var(--baosys-workspace-padding-left);
  overflow-y: auto;
  overflow-x: hidden;
}
</style>

<style>
/* Only the SPA shell owns the viewport. Public/login pages keep normal scrolling. */
html:has(.admin-layout),
html:has(.admin-layout) body,
html:has(.admin-layout) #app {
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
</style>
