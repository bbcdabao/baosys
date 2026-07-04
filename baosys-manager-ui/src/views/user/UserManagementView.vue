<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p>{{ t('user.description') }}</p>
      </div>
      <a-button v-if="canCreate" type="primary" @click="openCreate">
        <template #icon><icon-plus /></template>
        {{ t('common.create') }}
      </a-button>
    </div>

    <a-card :bordered="false">
      <a-form :model="filters" layout="inline" class="filter-form">
        <a-form-item field="name" :label="t('user.name')">
          <a-input
            v-model="filters.name"
            allow-clear
            :placeholder="t('user.namePlaceholder')"
            @press-enter="searchUsers"
          />
        </a-form-item>
        <a-form-item field="systemType" :label="t('user.systemType')">
          <a-select
            v-model="filters.systemType"
            allow-clear
            :placeholder="t('user.allPlatforms')"
          >
            <a-option
              v-for="option in SYSTEM_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ t(option.labelKey) }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="loading" @click="searchUsers">
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
          <a-tag color="arcoblue">
            {{ t(systemTypeLabelKey(record.systemType)) }}
          </a-tag>
        </template>
        <template #permissions="{ record }">
          <a-space wrap>
            <a-tag v-for="permission in record.permissions" :key="permission">
              {{ t(permissionLabelKey(record.systemType, permission)) }}
            </a-tag>
          </a-space>
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
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
        <a-form-item field="name" :label="t('user.name')">
          <a-input v-model="form.name" :placeholder="t('user.namePlaceholder')" />
        </a-form-item>
        <a-form-item field="pass" :label="t('user.password')">
          <a-input-password
            v-model="form.pass"
            :placeholder="t(editingId === null ? 'user.passwordPlaceholder' : 'user.editPasswordPlaceholder')"
          />
        </a-form-item>
        <a-form-item field="systemType" :label="t('user.systemType')">
          <a-select v-model="form.systemType" @change="clearSelectedPermissions">
            <a-option
              v-for="option in SYSTEM_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ t(option.labelKey) }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="permissions" :label="t('user.permissions')">
          <a-select
            v-model="form.permissions"
            multiple
            allow-clear
            :disabled="currentPermissionOptions.length === 0"
          >
            <a-option
              v-for="permission in currentPermissionOptions"
              :key="permission.code"
              :value="permission.code"
            >
              {{ t(permission.labelKey) }}
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="editorVisible = false">{{ t('common.cancel') }}</a-button>
          <a-button type="primary" :loading="saving" @click="saveUser">
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
import {
  permissionLabelKey,
  permissionOptionsFor
} from '@/constants/permissions'
import { SYSTEM_TYPE_OPTIONS, systemTypeLabelKey } from '@/constants/systemTypes'
import { useAuthStore } from '@/store/auth'
import type { SystemType, UserSummary } from '@/types/user'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const editingId = ref<number | null>(null)
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
  systemType: 'MANAGER' as SystemType,
  permissions: [] as string[]
})

const hasPermission = (permission: string): boolean =>
  authStore.currentUser?.permissions.includes(permission) === true
const canCreate = computed(() => hasPermission('user:add'))
const canUpdate = computed(() => hasPermission('user:update'))
const canDelete = computed(() => hasPermission('user:delete'))
const canViewSessions = computed(() => hasPermission('user:session:list'))
const currentPermissionOptions = computed(() =>
  permissionOptionsFor(form.systemType)
)

const columns = computed<TableColumnData[]>(() => [
  { title: t('user.name'), dataIndex: 'name' },
  { title: t('user.systemType'), slotName: 'systemType', width: 150 },
  { title: t('user.permissions'), slotName: 'permissions' },
  { title: t('common.status'), slotName: 'status', width: 120 },
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
  pass: editingId.value === null
    ? [{ required: true, minLength: 8, message: t('user.passwordRequired') }]
    // 编辑时空密码表示保持原密码，因此只校验实际输入的新密码。
    : [{ minLength: 8, message: t('user.passwordRequired') }],
  systemType: [{ required: true, message: t('user.systemTypeRequired') }],
  permissions: currentPermissionOptions.value.length > 0
    ? [{ required: true, message: t('user.permissionsRequired') }]
    : []
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
  // 权限不能跨系统沿用，切换平台后必须基于新平台的权限目录重新选择。
  form.permissions = []
}

const openCreate = (): void => {
  editingId.value = null
  Object.assign(form, {
    name: '',
    pass: '',
    systemType: 'MANAGER',
    permissions: ['user:list']
  })
  editorVisible.value = true
}

const openEdit = (user: UserSummary): void => {
  editingId.value = user.id
  const availablePermissionCodes = new Set(
    permissionOptionsFor(user.systemType).map(permission => permission.code)
  )
  Object.assign(form, {
    name: user.name,
    pass: '',
    systemType: user.systemType,
    // 过滤历史数据，避免已不属于当前系统的权限被编辑表单再次提交。
    permissions: user.permissions.filter(permission =>
      availablePermissionCodes.has(permission)
    )
  })
  editorVisible.value = true
}

const saveUser = async (): Promise<void> => {
  const validation = await formRef.value?.validate()
  if (validation) return

  saving.value = true
  try {
    if (editingId.value === null) {
      await createUser({ ...form })
      Message.success(t('user.created'))
    } else {
      await updateUser({ ...form, id: editingId.value })
      Message.success(t('user.updated'))
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
    if (users.value.length === 1 && currentPage.value > 1) currentPage.value--
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
.page-section { display: grid; gap: 18px; }
.page-heading { display: flex; justify-content: space-between; align-items: center; }
.page-heading h2 { margin: 0; color: var(--color-text-1); font-size: 22px; }
.page-heading p { margin: 6px 0 0; color: var(--color-text-3); }
.filter-form { margin-bottom: 18px; }
.filter-form :deep(.arco-form-item) { margin-bottom: 12px; }
.filter-form :deep(.arco-input-wrapper), .filter-form :deep(.arco-select-view) { width: 220px; }
</style>
