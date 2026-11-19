<template>
  <ACard class="page-card"
    ><template
      v-for="(_, name) in $slots"
      #[name]="scope"
      ><slot
        :name="name"
        v-bind="scope || {}" /></template
  ></ACard>
</template>
<script setup lang="ts">
import { Card as ACard } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/card/style/css.js'
</script>
