<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p>{{ t('user.description') }}</p>
      </div>
      <a-button
        v-if="canCreate"
        type="primary"
        @click="openCreate"
      >
        <template #icon><icon-plus /></template>
        {{ t('common.create') }}
      </a-button>
    </div>

    <a-card :bordered="false">
      <a-form
        :model="filters"
        layout="inline"
        class="filter-form"
      >
        <a-form-item
          field="name"
          :label="t('user.name')"
        >
          <a-input
            v-model="filters.name"
            allow-clear
            :placeholder="t('user.namePlaceholder')"
            @press-enter="searchUsers"
          />
        </a-form-item>
        <a-form-item
          field="systemType"
          :label="t('user.systemType')"
        >
          <a-select
            v-model="filters.systemType"
            allow-clear
            :placeholder="t('user.allPlatforms')"
          >
            <a-option
              v-for="option in systemOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button
              type="primary"
              :loading="loading"
              @click="searchUsers"
            >
              <template #icon><icon-search /></template>
              {{ t('common.query') }}
            </a-button>
            <a-button @click="resetFilters">{{ t('common.reset') }}</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-table
        row-key="id"
        :columns="columns"
        :data="users"
        :loading="loading"
        :pagination="pagination"
        @page-change="changePage"
      >
        <template #systemType="{ record }">
          <a-tag
            v-for="type in systemTypesFromPermissions(record.permissions)"
            :key="type"
            color="arcoblue"
          >
            {{ systemLabel(type) }}
          </a-tag>
        </template>
        <template #defaultSystemType="{ record }">
          {{ systemLabel(record.defaultSystemType) }}
        </template>
        <template #permissions="{ record }">
          <a-space wrap>
            <a-tag
              v-for="permission in record.permissions"
              :key="permission"
            >
              {{ permissionLabel(permission) }}
            </a-tag>
          </a-space>
        </template>
        <template #enabled="{ record }">
          <a-tag :color="record.enabled ? 'green' : 'gray'">
            {{ t(record.enabled ? 'common.enabled' : 'common.disabled') }}
          </a-tag>
        </template>
        <template #status="{ record }">
          <a-badge
            :status="record.status ? 'success' : 'normal'"
            :text="t(record.status ? 'common.online' : 'common.offline')"
          />
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button
              v-if="canViewSessions"
              type="text"
              size="small"
              @click="openLoginManagement(record)"
            >
              {{ t('user.loginManagement') }}
            </a-button>
            <a-button
              v-if="canUpdate"
              type="text"
              size="small"
              @click="openEdit(record)"
            >
              {{ t('common.edit') }}
            </a-button>
            <a-popconfirm
              v-if="canDelete"
              :content="t('user.deleteConfirm')"
              @ok="removeUser(record)"
            >
              <a-button
                type="text"
                status="danger"
                size="small"
                :disabled="record.id === authStore.currentUser?.userId"
              >
                {{ t('common.delete') }}
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:visible="editorVisible"
      :width="460"
      :title="t(editingId === null ? 'user.createTitle' : 'user.editTitle')"
      :unmount-on-close="true"
    >
      <a-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        layout="vertical"
      >
        <a-form-item
          field="name"
          :label="t('user.name')"
        >
          <a-input
            v-model="form.name"
            :placeholder="t('user.namePlaceholder')"
          />
        </a-form-item>
        <a-form-item
          field="pass"
          :label="t('user.password')"
        >
          <a-input-password
            v-model="form.pass"
            :placeholder="
              t(editingId === null ? 'user.passwordPlaceholder' : 'user.editPasswordPlaceholder')
            "
          />
        </a-form-item>
        <a-form-item
          field="systemTypes"
          :label="t('user.systemType')"
        >
          <a-select
            v-model="form.systemTypes"
            multiple
            allow-clear
            @change="clearSelectedPermissions"
          >
            <a-option
              v-for="option in systemOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="defaultSystemType" :label="t('user.defaultSystemType')">
          <a-select v-model="form.defaultSystemType" :placeholder="t('user.defaultSystemRequired')">
            <a-option v-for="option in systemOptions" :key="option.value" :value="option.value">
              {{ option.label }} ({{ option.value }})
            </a-option>
          </a-select>
          <template #extra>{{ t('user.defaultSystemHint') }}</template>
        </a-form-item>
        <a-form-item
          v-for="type in form.systemTypes"
          :key="type"
          :label="systemLabel(type)"
        >
          <a-checkbox-group
            v-if="permissionOptionsFor(type).length"
            v-model="form.permissions"
          >
            <a-checkbox
              v-for="permission in permissionOptionsFor(type)"
              :key="permission.code"
              :value="permission.code"
            >
              {{ permission.label }}
            </a-checkbox>
          </a-checkbox-group>
          <span v-else>{{ t('user.entryOnly') }}</span>
        </a-form-item>
        <a-form-item
          field="enabled"
          :label="t('user.accountStatus')"
        >
          <a-switch v-model="form.enabled" />
          <span class="field-hint">{{ t('user.enabledHint') }}</span>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="editorVisible = false">{{ t('common.cancel') }}</a-button>
          <a-button
            type="primary"
            :loading="saving"
            @click="saveUser"
          >
            {{ t('common.save') }}
          </a-button>
        </a-space>
      </template>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import type { FormInstance, TableColumnData } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/message/style/css.js'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { createUser, deleteUser, listUsers, updateUser } from '@/api/user'
import { useSystemCatalog } from '@/composables/useSystemCatalog'
import { systemTypesFromPermissions } from '@/utils/permissions'

import { useAuthStore } from '@/store/auth'
import type { SystemType, UserSummary } from '@/types/user'

const { t } = useI18n()
const { systemOptions, systemLabel, permissionOptionsFor, permissionLabel } = useSystemCatalog()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const originalUser = ref<UserSummary | null>(null)
const users = ref<UserSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const formRef = ref<FormInstance>()

const filters = reactive<{ name: string; systemType?: SystemType }>({
  name: '',
  systemType: undefined
})
const form = reactive({
  name: '',
  pass: '',
  defaultSystemType: '',
  systemTypes: [] as SystemType[],
  permissions: [] as string[],
  enabled: true
})

const hasPermission = (permission: string): boolean =>
  authStore.currentUser?.permissions.includes(permission) === true
const canCreate = computed(() => hasPermission('MGR:user:add'))
const canUpdate = computed(() => hasPermission('MGR:user:update'))
const canDelete = computed(() => hasPermission('MGR:user:delete'))
const canViewSessions = computed(() => hasPermission('MGR:user:session:list'))
const currentPermissionOptions = computed(() =>
  form.systemTypes.flatMap((type) => permissionOptionsFor(type))
)

const columns = computed<TableColumnData[]>(() => [
  { title: t('user.name'), dataIndex: 'name' },
  { title: t('user.systemType'), slotName: 'systemType', width: 150 },
  { title: t('user.defaultSystemType'), slotName: 'defaultSystemType', width: 150 },
  { title: t('user.permissions'), slotName: 'permissions' },
  { title: t('user.accountStatus'), slotName: 'enabled', width: 120 },
  { title: t('user.onlineStatus'), slotName: 'status', width: 120 },
  { title: t('common.actions'), slotName: 'actions', width: 260 }
])
const pagination = computed(() => ({
  current: currentPage.value,
  pageSize,
  total: total.value,
  showTotal: true
}))
const formRules = computed(() => ({
  name: [{ required: true, message: t('user.nameRequired') }],
  pass:
    editingId.value === null
      ? [{ required: true, minLength: 8, message: t('user.passwordRequired') }]
      : [{ minLength: 8, message: t('user.passwordRequired') }],
  defaultSystemType: [{ required: true, message: t('user.defaultSystemRequired') }],
  permissions: []
}))

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : t('common.serviceUnavailable')

const loadUsers = async (): Promise<void> => {
  loading.value = true
  try {
    const result = await listUsers(
      filters.name.trim(),
      filters.systemType,
      currentPage.value - 1,
      pageSize
    )
    users.value = result.items
    total.value = result.total
  } catch (error) {
    Message.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

const searchUsers = async (): Promise<void> => {
  currentPage.value = 1
  await loadUsers()
}

const resetFilters = async (): Promise<void> => {
  filters.name = ''
  filters.systemType = undefined
  await searchUsers()
}

const changePage = async (page: number): Promise<void> => {
  currentPage.value = page
  await loadUsers()
}

const clearSelectedPermissions = (): void => {
  const available = new Set(currentPermissionOptions.value.map((permission) => permission.code))
  form.permissions = form.permissions.filter((permission) => available.has(permission))
}

const openCreate = (): void => {
  editingId.value = null
  originalUser.value = null
  Object.assign(form, {
    name: '',
    pass: '',
    defaultSystemType: '',
    systemTypes: [],
    permissions: [],
    enabled: true
  })
  editorVisible.value = true
}

const openEdit = (user: UserSummary): void => {
  editingId.value = user.id
  originalUser.value = { ...user, permissions: [...user.permissions] }
  const availableSystems = new Set(systemOptions.value.map((option) => option.value))
  const selectedSystems = systemTypesFromPermissions(user.permissions).filter((type) =>
    availableSystems.has(type)
  )
  const availablePermissionCodes = new Set(
    selectedSystems
      .flatMap((type) => permissionOptionsFor(type))
      .map((permission) => permission.code)
  )
  const validGrants = new Set([...selectedSystems, ...availablePermissionCodes])
  const unknown = user.permissions.filter((permission) => !validGrants.has(permission))
  if (unknown.length) {
    Message.warning(`${t('user.retiredPermissions')}: ${unknown.join(', ')}`)
  }
  Object.assign(form, {
    defaultSystemType: user.defaultSystemType,
    enabled: user.enabled,
    name: user.name,
    pass: '',
    systemTypes: selectedSystems,
    // 过滤历史数据，避免已不属于当前系统的权限被编辑表单再次提交。
    permissions: user.permissions.filter((permission) => availablePermissionCodes.has(permission))
  })
  editorVisible.value = true
}

const saveUser = async (): Promise<void> => {
  const validation = await formRef.value?.validate()
  if (validation) {
    return
  }

  const payload = {
    name: form.name,
    pass: form.pass,
    defaultSystemType: form.defaultSystemType,
    enabled: form.enabled,
    permissions: [...form.systemTypes, ...form.permissions]
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await createUser(payload)
      Message.success(t('user.created'))
    } else {
      await updateUser({ ...payload, id: editingId.value })
      const original = originalUser.value
      const accessChanged =
        original &&
        (original.enabled !== form.enabled ||
          form.pass.trim().length > 0 ||
          JSON.stringify([...original.permissions].sort()) !==
            JSON.stringify([...payload.permissions].sort()))
      Message.success(t(accessChanged ? 'user.accessChangeSessionsRevoked' : 'user.updated'))
    }
    editorVisible.value = false
    await loadUsers()
  } catch (error) {
    Message.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

const removeUser = async (user: UserSummary): Promise<void> => {
  try {
    await deleteUser(user.id)
    if (users.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    await loadUsers()
    Message.success(t('user.deleted'))
  } catch (error) {
    Message.error(errorMessage(error))
  }
}

const openLoginManagement = (user: UserSummary): void => {
  // userId 用于精确查询，userName 只负责在目标页面回显。
  void router.push({
    name: 'login-management',
    query: { userId: String(user.id), userName: user.name }
  })
}

onMounted(loadUsers)
</script>

<style scoped>
.field-hint {
  margin-left: 12px;
  color: var(--color-text-3);
  font-size: 12px;
}
.page-section {
  display: grid;
  gap: 18px;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.page-heading h2 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 22px;
}
.page-heading p {
  margin: 6px 0 0;
  color: var(--color-text-3);
}
.filter-form {
  margin-bottom: 18px;
}
.filter-form :deep(.arco-form-item) {
  margin-bottom: 12px;
}
.filter-form :deep(.arco-input-wrapper),
.filter-form :deep(.arco-select-view) {
  width: 220px;
}
</style>
