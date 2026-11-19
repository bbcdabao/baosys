<template>
  <a-layout-header class="admin-header">
    <div><slot /></div>
    <a-space
      class="header-actions"
      :wrap="wrapActions"
    >
      <slot name="actions" />
      <a-select
        :model-value="locale"
        :aria-label="languageLabel"
        @update:model-value="(value) => emit('update:locale', String(value))"
        size="small"
        class="locale-select"
      >
        <a-option
          v-for="option in localeOptions"
          :key="option.value"
          :value="option.value"
          >{{ option.label }}</a-option
        >
      </a-select>
      <a-tooltip :content="labels.theme">
        <a-button
          shape="circle"
          :aria-label="labels.theme"
          @click="emit('toggleTheme')"
        >
          <icon-sun-fill v-if="isDark" />
          <icon-moon-fill v-else />
        </a-button>
      </a-tooltip>
      <a-tooltip :content="fullscreen ? labels.exitFullscreen : labels.fullscreen">
        <a-button
          shape="circle"
          :aria-label="fullscreen ? labels.exitFullscreen : labels.fullscreen"
          :aria-pressed="fullscreen"
          data-fullscreen-toggle
          @click="toggleFullscreen"
        >
          <icon-fullscreen-exit v-if="fullscreen" />
          <icon-fullscreen v-else />
        </a-button>
      </a-tooltip>
      <a-tooltip :content="userActionLabel">
        <a-button
          class="user-chip"
          type="text"
          :aria-label="userActionLabel"
          @click="emit('userClick')"
        >
          <template #icon><icon-user /></template>
          <span class="user-name">{{ userName }}</span>
        </a-button>
      </a-tooltip>
      <a-button
        type="text"
        :loading="loggingOut"
        @click="emit('logout')"
      >
        <template #icon><icon-export /></template>
        {{ labels.logout }}
      </a-button>
    </a-space>
  </a-layout-header>
</template>
<script setup lang="ts">
import { useFullscreen } from '@baosys/common-ui/preferences'
import {
  LayoutHeader as ALayoutHeader,
  Space as ASpace,
  Select as ASelect,
  Option as AOption,
  Tooltip as ATooltip,
  Button as AButton
} from '@arco-design/web-vue'
import {
  IconSunFill,
  IconMoonFill,
  IconFullscreenExit,
  IconFullscreen,
  IconUser,
  IconExport
} from '@arco-design/web-vue/es/icon'
import '@arco-design/web-vue/es/layout/style/css.js'
import '@arco-design/web-vue/es/space/style/css.js'
import '@arco-design/web-vue/es/select/style/css.js'
import '@arco-design/web-vue/es/tooltip/style/css.js'
import '@arco-design/web-vue/es/button/style/css.js'
import type { HeaderLabels, LocaleOption } from './types'
withDefaults(
  defineProps<{
    locale: string
    localeOptions: LocaleOption[]
    isDark: boolean
    userName?: string
    userActionLabel: string
    loggingOut?: boolean
    labels: HeaderLabels
    languageLabel?: string
    wrapActions?: boolean
  }>(),
  { wrapActions: true }
)
const emit = defineEmits<{
  'update:locale': [value: string]
  toggleTheme: []
  logout: []
  userClick: []
  fullscreenError: []
}>()
const { fullscreen, toggleFullscreen } = useFullscreen(() => emit('fullscreenError'))
</script>
<style scoped>
.admin-header {
  min-height: var(--baosys-header-height);
  height: auto;
  flex-shrink: 0;
  padding: var(--baosys-header-padding-y) var(--baosys-header-padding-x);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg-2);
  border-bottom: var(--baosys-border-width) solid var(--color-border);
}
.admin-header h1 {
  margin: 4px 0 0;
  font-size: 20px;
  color: var(--color-text-1);
}
.header-actions {
  min-width: 0;
  max-width: 100%;
}
.locale-select {
  width: var(--baosys-locale-width);
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-2);
}
</style>
