<template>
  <div class="page-heading">
    <div>
      <h2 v-if="title">{{ title }}</h2>
      <p v-if="description">{{ description }}</p>
      <slot />
    </div>
    <slot name="actions" />
  </div>
</template>
<script setup lang="ts">
defineProps<{ title?: string; description?: string }>()
</script>
