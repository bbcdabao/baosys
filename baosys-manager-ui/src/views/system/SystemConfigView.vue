<template>
  <PageCard
    :bordered="false"
    class="building-card baosys-page"
  >
    <a-result
      :title="t('system.building')"
      :subtitle="t('system.description')"
    >
      <template #icon><icon-settings /></template>
    </a-result>
  </PageCard>
</template>

<script setup lang="ts">
import { PageCard } from '@baosys/common-ui/components'
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
