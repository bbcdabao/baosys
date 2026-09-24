<template>
  <main class="login-page">
    <div class="login-brand">BAOSYS<span aria-hidden="true"> / </span></div>
    <div class="login-tools">
      <a-select v-model="locale" size="small" :aria-label="t('common.language')">
        <a-option value="zh-CN">中文</a-option>
        <a-option value="en-US">English</a-option>
      </a-select>
      <a-button shape="circle" :aria-label="t(isDark ? 'common.light' : 'common.dark')" @click="toggleTheme">
        <icon-sun-fill v-if="isDark" />
        <icon-moon-fill v-else />
      </a-button>
    </div>

    <section class="login-intro">
      <p class="eyebrow">{{ t('login.subtitle') }}</p>
      <h1>
        <span>{{ t('login.userCenter') }}</span>
        <span>{{ t('login.configCenter') }}</span>
      </h1>
      <div class="center-descriptions">
        <p><span class="description-marker" aria-hidden="true" />{{ t('login.userDescription') }}</p>
        <p><span class="description-marker" aria-hidden="true" />{{ t('login.configDescription') }}</p>
      </div>
    </section>

    <a-card :bordered="false" class="login-card">
      <h2>{{ t('login.title') }}</h2>
      <a-form ref="formRef" :model="form" layout="vertical" @submit-success="submit">
        <a-form-item
          field="username"
          :label="t('login.username')"
          :rules="[{ required: true, message: t('login.usernameRequired') }]"
        >
          <a-input v-model="form.username" size="large" allow-clear>
            <template #prefix><icon-user /></template>
          </a-input>
        </a-form-item>
        <a-form-item
          field="password"
          :label="t('login.password')"
          :rules="[{ required: true, message: t('login.passwordRequired') }]"
        >
          <a-input-password v-model="form.password" size="large">
            <template #prefix><icon-lock /></template>
          </a-input-password>
        </a-form-item>
        <a-checkbox v-model="rememberUsername">
          {{ t('login.rememberUsername') }}
        </a-checkbox>
        <a-button
          class="submit-button"
          type="primary"
          html-type="submit"
          size="large"
          long
          :loading="authStore.loading"
        >
          {{ t('login.submit') }}
        </a-button>
      </a-form>
    </a-card>
  </main>
</template>

<script setup lang="ts">
import type { FormInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/message/style/css.js'
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/store/auth'

const USERNAME_STORAGE_KEY = 'baosys-remembered-username'
const { t } = useI18n()
const { locale } = useLocale()
const { isDark, toggleTheme } = useTheme()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const rememberUsername = ref(true)
const form = reactive({ username: '', password: '' })

const submit = async (): Promise<void> => {
  try {
    await authStore.login({
      username: form.username.trim(),
      password: form.password,
      systemType: 'MANAGER',
      loginClient: `${navigator.platform || 'browser'} / ${navigator.userAgent}`
    })
    if (rememberUsername.value) {
      localStorage.setItem(USERNAME_STORAGE_KEY, form.username.trim())
    } else {
      localStorage.removeItem(USERNAME_STORAGE_KEY)
    }
    const redirect = typeof route.query.redirect === 'string'
      && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/users-management'
    await router.replace(redirect)
    Message.success(t('login.success'))
  } catch (error) {
    Message.error(error instanceof Error ? error.message : t('login.failed'))
  }
}

onMounted(() => {
  form.username = localStorage.getItem(USERNAME_STORAGE_KEY) || ''
  rememberUsername.value = Boolean(form.username)
})
</script>

<style scoped>
.login-page {
  min-height: 100svh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 400px);
  align-items: center;
  gap: clamp(48px, 8vw, 128px);
  padding: 120px max(48px, calc((100vw - 1040px) / 2));
  position: relative;
  background: var(--color-bg-1);
}
.login-brand {
  position: absolute;
  top: 36px;
  left: 48px;
  color: var(--color-text-1);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: .16em;
}
.login-brand span { margin-left: 8px; color: rgb(var(--primary-6)); }
.login-tools { position: absolute; top: 28px; right: 48px; display: flex; align-items: center; gap: 12px; }
.login-tools :deep(.arco-select-view) { width: 100px; background: transparent; }
.login-tools :deep(.arco-btn) { background: transparent; }
.login-intro { color: var(--color-text-1); }
.eyebrow { margin: 0 0 24px; color: var(--color-text-3); font-size: 13px; letter-spacing: .12em; }
.login-intro h1 { margin: 0; font-size: clamp(36px, 4.2vw, 56px); font-weight: 600; line-height: 1.45; letter-spacing: -.04em; }
.login-intro h1 span { display: block; }
.center-descriptions { margin-top: 32px; display: flex; flex-wrap: wrap; gap: 12px 28px; }
.center-descriptions p { display: flex; align-items: center; gap: 10px; margin: 0; color: var(--color-text-2); font-size: 14px; line-height: 1.6; }
.description-marker { width: 5px; height: 5px; background: rgb(var(--primary-6)); border-radius: 50%; flex-shrink: 0; }
.login-card { width: 100%; padding: 12px 0 12px 48px; border-left: 1px solid var(--color-border-2); border-radius: 0; background: transparent; }
.login-card :deep(.arco-card-body) { padding: 0; }
.login-card h2 { margin: 0 0 32px; font-size: 24px; font-weight: 600; color: var(--color-text-1); }
.login-card :deep(.arco-input-wrapper) { min-height: 46px; border-radius: 6px; }
.submit-button { margin-top: 28px; height: 46px; border-radius: 6px; }
@media (max-width: 760px) {
  .login-page { grid-template-columns: minmax(0, 400px); justify-content: center; gap: 40px; padding: 120px 24px 48px; }
  .login-brand { top: 30px; left: 24px; font-size: 14px; }
  .login-tools { top: 22px; right: 20px; gap: 4px; }
  .eyebrow { margin-bottom: 16px; }
  .login-intro h1 { font-size: 36px; }
  .center-descriptions { margin-top: 20px; gap: 12px 20px; }
  .login-card { padding: 32px 0 0; border-left: 0; border-top: 1px solid var(--color-border-2); }
  .login-card h2 { margin-bottom: 24px; }
}
</style>
