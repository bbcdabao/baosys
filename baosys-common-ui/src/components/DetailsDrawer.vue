<template>
  <ADrawer
    v-model:visible="visible"
    width="var(--baosys-detail-width)"
    :footer="false"
    unmount-on-close
    ><div class="record-details"><slot /></div
  ></ADrawer>
</template>
<script setup lang="ts">
import { Drawer as ADrawer } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/drawer/style/css.js'
const visible = defineModel<boolean>('visible', { default: false })
</script>
