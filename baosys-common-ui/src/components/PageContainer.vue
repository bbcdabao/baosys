<template>
  <section
    class="baosys-page"
    :class="{ 'page-section': !flow }"
  >
    <slot />
  </section>
</template>
<script setup lang="ts">
defineProps<{ flow?: boolean }>()
</script>
