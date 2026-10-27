<template>
  <main class="login-page">
    <div class="login-brand">BAOSYS<span aria-hidden="true"> / </span></div>
    <div class="login-tools">
      <a-select
        v-model="locale"
        size="small"
        :aria-label="t('common.language')"
      >
        <a-option value="zh-CN">中文</a-option>
        <a-option value="en-US">English</a-option>
      </a-select>
      <a-button
        shape="circle"
        :aria-label="t(isDark ? 'common.light' : 'common.dark')"
        @click="toggleTheme"
      >
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
        <p>
          <span
            class="description-marker"
            aria-hidden="true"
          />{{ t('login.userDescription') }}
        </p>
        <p>
          <span
            class="description-marker"
            aria-hidden="true"
          />{{ t('login.configDescription') }}
        </p>
      </div>
    </section>

    <a-card
      :bordered="false"
      class="login-card"
    >
      <h2>{{ t('login.title') }}</h2>
      <a-form
        :model="form"
        layout="vertical"
        @submit-success="submit"
      >
        <a-form-item
          field="username"
          :label="t('login.username')"
          :rules="[{ required: true, message: t('login.usernameRequired') }]"
        >
          <a-input
            v-model="form.username"
            autocomplete="username"
            :max-length="128"
          />
        </a-form-item>
        <a-form-item
          field="password"
          :label="t('login.password')"
          :rules="[{ required: true, message: t('login.passwordRequired') }]"
        >
          <a-input-password
            v-model="form.password"
            autocomplete="current-password"
            :max-length="256"
          />
        </a-form-item>
        <a-alert
          v-if="error"
          type="error"
          class="login-error"
          >{{ error }}</a-alert
        >
        <a-button
          type="primary"
          html-type="submit"
          size="large"
          long
          :loading="submitting"
        >
          {{ t('login.submit') }}
        </a-button>
      </a-form>
    </a-card>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { login } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { useLocale } from '@/composables/useLocale'
import { useTheme } from '@/composables/useTheme'
const { t } = useI18n()
const { locale } = useLocale()
const { isDark, toggleTheme } = useTheme()
const route = useRoute()
const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const error = ref('')
const submit = async (): Promise<void> => {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const target = route.query.redirect_url ?? route.query.redirect
    const result = await login(
      form.username,
      form.password,
      typeof target === 'string' ? target : undefined
    )
    auth.clearSession()
    form.password = ''
    window.location.replace(result.redirectUrl)
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : t('login.failed')
    form.password = ''
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-error {
  margin-bottom: 16px;
}
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
  letter-spacing: 0.16em;
}
.login-brand span {
  margin-left: 8px;
  color: rgb(var(--primary-6));
}
.login-tools {
  position: absolute;
  top: 28px;
  right: 48px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.login-tools :deep(.arco-select-view) {
  width: 100px;
  background: transparent;
}
.login-tools :deep(.arco-btn) {
  background: transparent;
}
.login-intro {
  color: var(--color-text-1);
}
.eyebrow {
  margin: 0 0 24px;
  color: var(--color-text-3);
  font-size: 13px;
  letter-spacing: 0.12em;
}
.login-intro h1 {
  margin: 0;
  font-size: clamp(36px, 4.2vw, 56px);
  font-weight: 600;
  line-height: 1.45;
  letter-spacing: -0.04em;
}
.login-intro h1 span {
  display: block;
}
.center-descriptions {
  margin-top: 32px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
}
.center-descriptions p {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: var(--color-text-2);
  font-size: 14px;
  line-height: 1.6;
}
.description-marker {
  width: 5px;
  height: 5px;
  background: rgb(var(--primary-6));
  border-radius: 50%;
  flex-shrink: 0;
}
.login-card {
  width: 100%;
  padding: 12px 0 12px 48px;
  border-left: 1px solid var(--color-border-2);
  border-radius: 0;
  background: transparent;
}
.login-card :deep(.arco-card-body) {
  padding: 0;
}
.login-card h2 {
  margin: 0 0 32px;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text-1);
}
.login-card :deep(.arco-input-wrapper) {
  min-height: 46px;
  border-radius: 6px;
}
.submit-button {
  margin-top: 28px;
  height: 46px;
  border-radius: 6px;
}
@media (max-width: 760px) {
  .login-page {
    grid-template-columns: minmax(0, 400px);
    justify-content: center;
    gap: 40px;
    padding: 120px 24px 48px;
  }
  .login-brand {
    top: 30px;
    left: 24px;
    font-size: 14px;
  }
  .login-tools {
    top: 22px;
    right: 20px;
    gap: 4px;
  }
  .eyebrow {
    margin-bottom: 16px;
  }
  .login-intro h1 {
    font-size: 36px;
  }
  .center-descriptions {
    margin-top: 20px;
    gap: 12px 20px;
  }
  .login-card {
    padding: 32px 0 0;
    border-left: 0;
    border-top: 1px solid var(--color-border-2);
  }
  .login-card h2 {
    margin-bottom: 24px;
  }
}
</style>
