<template>
  <div
    class="baosys-page-state"
    role="status"
  >
    <ASpin
      v-if="loading"
      :tip="message"
    />
    <AResult
      v-else-if="error"
      status="error"
      :title="message"
      ><template #extra
        ><AButton
          v-if="retryLabel"
          @click="$emit('retry')"
          >{{ retryLabel }}</AButton
        ><slot /></template
    ></AResult>
    <AEmpty
      v-else
      :description="message"
      ><slot>{{ message }}</slot></AEmpty
    >
  </div>
</template>
<script setup lang="ts">
import {
  Spin as ASpin,
  Result as AResult,
  Button as AButton,
  Empty as AEmpty
} from '@arco-design/web-vue'
import '@arco-design/web-vue/es/spin/style/css.js'
import '@arco-design/web-vue/es/result/style/css.js'
import '@arco-design/web-vue/es/button/style/css.js'
import '@arco-design/web-vue/es/empty/style/css.js'
defineProps<{ loading?: boolean; error?: boolean; message: string; retryLabel?: string }>()
defineEmits<{ retry: [] }>()
</script>
