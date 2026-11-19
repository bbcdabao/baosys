<template>
  <ATable class="management-table">
    <template
      v-for="(_, name) in $slots"
      #[name]="scope"
    >
      <slot
        :name="name"
        v-bind="scope || {}"
      />
    </template>
  </ATable>
</template>
<script setup lang="ts">
import { Table as ATable } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/table/style/css.js'
</script>
<style scoped>
/* Keep management tables within the available width, including long values. */
.management-table {
  min-width: 0;
  max-width: 100%;
}
.management-table :deep(.arco-table-container),
.management-table :deep(.arco-table-content),
.management-table :deep(.arco-table-body) {
  overflow-x: hidden;
}
.management-table :deep(.arco-table-element) {
  width: 100%;
  table-layout: fixed;
}
.management-table :deep(.arco-table-cell) {
  white-space: normal;
  overflow-wrap: anywhere;
}
.management-table :deep(.arco-tag) {
  height: auto;
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}
/* Ten full-width characters plus the cell padding. */
.management-table :deep(col:first-child) {
  width: calc(10em + 32px);
}
.management-table :deep(.table-detail-link) {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: rgb(var(--primary-6));
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}
.management-table :deep(.table-detail-link:hover) {
  text-decoration: underline;
}

.management-table :deep(.arco-pagination) {
  flex-wrap: wrap;
  gap: 4px;
}
@media (max-width: 600px) {
  .management-table :deep(col:first-child) {
    width: 35% !important;
    min-width: 0 !important;
  }
  .management-table :deep(.arco-table-cell) {
    padding: 9px 6px;
  }
}
</style>
