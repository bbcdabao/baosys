<template>
  <main class="login-page">
    <div class="login-tools">
      <a-select v-model="locale" size="small">
        <a-option value="zh-CN">中文</a-option>
        <a-option value="en-US">English</a-option>
      </a-select>
      <a-button shape="circle" @click="toggleTheme">
        <icon-sun-fill v-if="isDark" />
        <icon-moon-fill v-else />
      </a-button>
    </div>

    <section class="login-intro">
      <div class="ocean-mark"><icon-sound /></div>
      <p class="eyebrow">BAOSYS</p>
      <h1>{{ t('app.name') }}</h1>
      <p>{{ t('login.subtitle') }}</p>
      <div class="wave" aria-hidden="true">
        <span v-for="index in 28" :key="index" :style="{ height: `${signalHeight(index)}px` }" />
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

const signalHeight = (index: number): number =>
  Math.round(10 + Math.abs(Math.sin(index * 0.72)) * 38)

onMounted(() => {
  form.username = localStorage.getItem(USERNAME_STORAGE_KEY) || ''
  rememberUsername.value = Boolean(form.username)
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1.1fr) minmax(380px, .9fr);
  align-items: center;
  gap: 8vw;
  padding: 8vw;
  position: relative;
  background:
    radial-gradient(circle at 12% 18%, rgba(22, 175, 202, .22), transparent 32%),
    linear-gradient(135deg, var(--color-bg-1), var(--color-fill-2));
}
.login-tools { position: absolute; top: 24px; right: 28px; display: flex; gap: 10px; }
.login-tools :deep(.arco-select-view) { width: 105px; }
.login-intro { max-width: 620px; color: var(--color-text-1); }
.ocean-mark { font-size: 56px; color: rgb(var(--primary-6)); }
.eyebrow { letter-spacing: .28em; color: rgb(var(--primary-6)); font-weight: 700; }
.login-intro h1 { margin: 8px 0 14px; font-size: clamp(36px, 5vw, 64px); }
.login-intro > p:last-of-type { color: var(--color-text-2); font-size: 18px; }
.wave { height: 72px; display: flex; align-items: center; gap: 6px; margin-top: 38px; }
.wave span { width: 4px; border-radius: 4px; background: linear-gradient(rgb(var(--primary-5)), rgb(var(--cyan-5))); opacity: .8; }
.login-card { width: 100%; max-width: 440px; padding: 22px; box-shadow: 0 24px 70px rgba(7, 30, 45, .16); }
.login-card h2 { margin: 0 0 28px; font-size: 26px; color: var(--color-text-1); }
.submit-button { margin-top: 24px; }
@media (max-width: 900px) {
  .login-page { grid-template-columns: 1fr; padding: 80px 24px 36px; }
  .login-intro { display: none; }
  .login-card { margin: auto; }
}
</style>
