<template>
  <AForm
    class="filter-form"
    layout="inline"
    :model="model"
    ><slot
  /></AForm>
</template>
<script setup lang="ts">
import { Form as AForm } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/form/style/css.js'
defineProps<{ model: Record<string, unknown> }>()
</script>
