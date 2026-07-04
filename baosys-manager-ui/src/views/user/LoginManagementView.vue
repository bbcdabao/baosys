<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p>{{ t('session.description') }}</p>
      </div>
      <a-popconfirm
        v-if="exactUserId && canKick"
        :content="t('session.kickAllConfirm')"
        @ok="kickAll"
      >
        <a-button status="danger">{{ t('session.kickAll') }}</a-button>
      </a-popconfirm>
    </div>

    <a-card :bordered="false">
      <a-form :model="filters" layout="inline" class="filter-form">
        <a-form-item field="userName" :label="t('session.userName')">
          <a-input
            v-model="filters.userName"
            allow-clear
            :placeholder="t('session.userNamePlaceholder')"
            @press-enter="search"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="loading" @click="search">
              <template #icon><icon-search /></template>
              {{ t('common.query') }}
            </a-button>
            <a-button @click="reset">{{ t('common.reset') }}</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-table
        row-key="id"
        :columns="columns"
        :data="sessions"
        :loading="loading"
        :pagination="pagination"
        @page-change="changePage"
      >
        <template #systemType="{ record }">
          <a-tag color="arcoblue">{{ t(systemTypeLabelKey(record.systemType)) }}</a-tag>
        </template>
        <template #loginAt="{ record }">{{ formatTime(record.loginAt) }}</template>
        <template #expiresAt="{ record }">{{ formatTime(record.expiresAt) }}</template>
        <template #actions="{ record }">
          <a-popconfirm
            v-if="canKick"
            :content="t('session.kickConfirm')"
            @ok="kickOne(record)"
          >
            <a-button type="text" status="danger" size="small">
              {{ t('session.kick') }}
            </a-button>
          </a-popconfirm>
        </template>
      </a-table>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import type { TableColumnData } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/message/style/css.js'
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { listLoginSessions } from '@/api/loginSession'
import { kickAllLoginClients, kickLoginClient } from '@/api/user'
import { systemTypeLabelKey } from '@/constants/systemTypes'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/store/auth'
import type { LoginSession } from '@/types/loginSession'

const { t } = useI18n()
const { locale } = useLocale()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const sessions = ref<LoginSession[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const exactUserId = ref<number>()
const exactUserName = ref('')
const filters = reactive({ userName: '' })

const canKick = computed(() =>
  authStore.currentUser?.permissions.includes('user:session:kick') === true
)
const columns = computed<TableColumnData[]>(() => [
  { title: t('session.userName'), dataIndex: 'userName', width: 160 },
  { title: t('user.systemType'), slotName: 'systemType', width: 150 },
  { title: t('session.client'), dataIndex: 'loginClient' },
  { title: t('session.loginAt'), slotName: 'loginAt', width: 190 },
  { title: t('session.expiresAt'), slotName: 'expiresAt', width: 190 },
  { title: t('common.actions'), slotName: 'actions', width: 110 }
])
const pagination = computed(() => ({
  current: currentPage.value,
  pageSize,
  total: total.value,
  showTotal: true
}))

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : t('common.serviceUnavailable')

const formatTime = (value: string): string =>
  new Intl.DateTimeFormat(locale.value, {
    dateStyle: 'medium',
    timeStyle: 'medium'
  }).format(new Date(value))

const loadSessions = async (): Promise<void> => {
  loading.value = true
  try {
    const result = await listLoginSessions(
      exactUserId.value,
      filters.userName.trim(),
      currentPage.value - 1,
      pageSize
    )
    sessions.value = result.items
    total.value = result.total
  } catch (error) {
    Message.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

const syncFromRoute = async (): Promise<void> => {
  const userId = Number(route.query.userId)
  exactUserId.value = Number.isSafeInteger(userId) && userId > 0
    ? userId
    : undefined
  exactUserName.value = typeof route.query.userName === 'string'
    ? route.query.userName
    : ''
  filters.userName = exactUserName.value
  currentPage.value = 1
  await loadSessions()
}

const search = async (): Promise<void> => {
  const normalized = filters.userName.trim()
  // 只有筛选值仍等于路由带入的账户名时才保留精确 userId；
  // 用户手工修改名称后降级为模糊查询，避免界面条件与实际查询不一致。
  const keepExactUser = exactUserId.value
    && normalized === exactUserName.value
  await router.replace({
    name: 'login-management',
    query: keepExactUser
      ? { userId: String(exactUserId.value), userName: exactUserName.value }
      : normalized
        ? { userName: normalized }
        : {}
  })
}

const reset = async (): Promise<void> => {
  await router.replace({ name: 'login-management' })
}

const changePage = async (page: number): Promise<void> => {
  currentPage.value = page
  await loadSessions()
}

const kickOne = async (session: LoginSession): Promise<void> => {
  try {
    await kickLoginClient(session.userId, session.id)
    // 删除当前页唯一记录时先回退，避免停留在空页。
    if (sessions.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadSessions()
    Message.success(t('session.kicked'))
  } catch (error) {
    Message.error(errorMessage(error))
  }
}

const kickAll = async (): Promise<void> => {
  if (!exactUserId.value) return
  try {
    await kickAllLoginClients(exactUserId.value)
    currentPage.value = 1
    await loadSessions()
    Message.success(t('session.kickedAll'))
  } catch (error) {
    Message.error(errorMessage(error))
  }
}

// URL 是筛选状态的唯一来源，菜单无 query 导航会可靠地恢复“全部登录”。
watch(() => route.fullPath, syncFromRoute, { immediate: true })
</script>

<style scoped>
.page-section { display: grid; gap: 18px; }
.page-heading { display: flex; justify-content: space-between; align-items: center; }
.page-heading h2 { margin: 0; color: var(--color-text-1); font-size: 22px; }
.page-heading p { margin: 6px 0 0; color: var(--color-text-3); }
.filter-form { margin-bottom: 18px; }
.filter-form :deep(.arco-input-wrapper) { width: 260px; }
</style>
