<template>
  <a-card
    :title="t('user.authorizedSystems')"
    :bordered="false"
  >
    <a-empty
      v-if="authorized.length === 0"
      :description="t('user.noSystemAccess')"
    />
    <a-tree
      v-else
      class="authorized-tree"
      :data="authorized"
      :selectable="false"
      default-expand-all
      show-line
      block-node
    >
      <template #title="node">
        <span
          v-if="node.homeUrl"
          class="system-node"
        >
          <icon-apps />
          <a
            :href="node.homeUrl"
            @click.stop
            >{{ node.title }}</a
          >
          <span
            v-if="node.children.length === 0"
            class="entry-hint"
            >{{ t('user.entryOnly') }}</span
          >
        </span>
        <span
          v-else
          class="permission-node"
        >
          <icon-safe />
          <span
            >{{ node.title }}<code v-if="node.title !== node.key">{{ node.key }}</code></span
          >
        </span>
      </template>
    </a-tree>
    <p>{{ t('user.systemAccessHint') }}</p>
  </a-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSystemCatalog } from '@/composables/useSystemCatalog'
import { useAuthStore } from '@/store/auth'
const { t } = useI18n()
const { systemOptions, permissionLabel } = useSystemCatalog()
const auth = useAuthStore()
const authorized = computed(() => {
  const permissions = [...new Set(auth.currentUser?.permissions ?? [])]
  return systemOptions.value
    .filter((system) => permissions.includes(system.value))
    .map((system) => ({
      key: system.value,
      title: system.label,
      homeUrl: system.homeUrl,
      children: permissions
        .filter((permission) => permission.startsWith(system.value + ':'))
        .map((permission) => ({
          key: permission,
          title: permissionLabel(permission),
          isLeaf: true
        }))
    }))
})
</script>

<style scoped>
.authorized-tree {
  min-width: 0;
}
.authorized-tree :deep(.arco-tree-node-title),
.authorized-tree :deep(.arco-tree-node-title-text) {
  min-width: 0;
  white-space: normal;
}
.system-node,
.permission-node {
  display: flex;
  align-items: baseline;
  gap: 8px;
  overflow-wrap: anywhere;
}
.system-node {
  flex-wrap: wrap;
}
.system-node a {
  color: rgb(var(--primary-6));
  text-decoration: none;
}
.system-node a:hover {
  text-decoration: underline;
}
.system-node .arco-icon,
.permission-node .arco-icon {
  flex-shrink: 0;
}
.permission-node > span {
  min-width: 0;
}
.entry-hint,
code,
p {
  color: var(--color-text-3);
}
code {
  display: block;
  font-size: 12px;
}
</style>
