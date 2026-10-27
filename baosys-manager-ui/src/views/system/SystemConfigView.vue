<template>
  <a-card
    :bordered="false"
    class="building-card"
  >
    <a-result
      :title="t('system.building')"
      :subtitle="t('system.description')"
    >
      <template #icon><icon-settings /></template>
    </a-result>
  </a-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped>
.building-card {
  min-height: 420px;
  display: grid;
  place-items: center;
}
</style>
