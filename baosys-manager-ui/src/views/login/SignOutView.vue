<template>
  <main class="sign-out-page">
    <header class="brand">
      BAOSYS<span> / </span><span class="brand-caption">{{ t('signOut.brand') }}</span>
    </header>
    <section
      class="sign-out-card"
      :aria-busy="state === 'working'"
    >
      <div
        class="status-symbol"
        :class="state"
        aria-hidden="true"
      >
        <span
          v-if="state === 'working'"
          class="spinner"
        />
        <svg
          v-else-if="state === 'done'"
          viewBox="0 0 24 24"
          fill="none"
        >
          <path d="m5 12 4 4L19 6" />
        </svg>
        <span v-else>!</span>
      </div>
      <p class="eyebrow">{{ t('signOut.eyebrow') }}</p>
      <div
        role="status"
        aria-live="polite"
      >
        <h1>
          {{
            t(
              state === 'done'
                ? 'signOut.done'
                : state === 'failed'
                  ? 'signOut.failed'
                  : 'signOut.title'
            )
          }}
        </h1>
        <p class="description">
          {{
            t(
              state === 'done'
                ? 'signOut.doneHint'
                : state === 'failed'
                  ? 'signOut.failedHint'
                  : 'signOut.description'
            )
          }}
        </p>
      </div>
      <ol class="steps">
        <li :class="{ complete: state === 'done' }">
          <span class="step-number">01</span><span>{{ t('signOut.session') }}</span
          ><span class="step-state">{{
            t(
              state === 'done'
                ? 'signOut.completed'
                : state === 'failed'
                  ? 'signOut.unconfirmed'
                  : 'signOut.pending'
            )
          }}</span>
        </li>
        <li :class="{ complete: state === 'done' }">
          <span class="step-number">02</span><span>{{ t('signOut.credentials') }}</span
          ><span class="step-state">{{
            t(
              state === 'done'
                ? 'signOut.completed'
                : state === 'failed'
                  ? 'signOut.unconfirmed'
                  : 'signOut.pending'
            )
          }}</span>
        </li>
        <li>
          <span class="step-number">03</span><span>{{ t('signOut.login') }}</span
          ><span class="step-state">{{
            t(state === 'done' ? 'signOut.redirecting' : 'signOut.waiting')
          }}</span>
        </li>
      </ol>
      <a-alert
        v-if="state === 'failed'"
        type="error"
        class="error"
        >{{ error }}</a-alert
      >
      <a-button
        v-if="state === 'failed'"
        type="primary"
        long
        @click="signOut"
        >{{ t('signOut.retry') }}</a-button
      >
      <a-button
        v-else-if="state === 'done'"
        type="text"
        @click="goLogin"
        >{{ t('signOut.continue') }}</a-button
      >
      <p
        v-else
        class="footnote"
      >
        {{ t('signOut.waitHint') }}
      </p>
    </section>
    <footer>{{ t('signOut.footer') }}</footer>
  </main>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute(),
  router = useRouter()
const state = ref<'working' | 'done' | 'failed'>('working')
const error = ref('')
let pending = false,
  disposed = false
let redirectTimer: ReturnType<typeof setTimeout> | undefined
const goLogin = () => {
  if (state.value !== 'done' || disposed) return
  clearTimeout(redirectTimer)
  const target = route.query.redirect_url
  // Only carry the return target into login; the server validates it after login.
  void router.replace({
    name: 'login',
    query: typeof target === 'string' ? { redirect_url: target } : {}
  })
}
const signOut = async () => {
  if (pending || state.value === 'done') return
  pending = true
  state.value = 'working'
  error.value = ''
  try {
    await auth.logout()
    if (disposed) return
    state.value = 'done'
    redirectTimer = setTimeout(goLogin, 1200)
  } catch (failure) {
    if (disposed) return
    state.value = 'failed'
    error.value = failure instanceof Error ? failure.message : t('signOut.failedHint')
  } finally {
    pending = false
  }
}
onMounted(signOut)
onUnmounted(() => {
  disposed = true
  clearTimeout(redirectTimer)
})
</script>

<style scoped>
.sign-out-page {
  min-height: 100svh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 104px 24px 64px;
  color: var(--color-text-1);
  background:
    radial-gradient(ellipse at 50% 30%, rgba(var(--primary-6), 0.055), transparent 65%),
    var(--color-bg-1);
}
.brand {
  position: absolute;
  top: 36px;
  left: 48px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0.13em;
}
.brand > span:first-child {
  margin: 0 12px;
  color: rgb(var(--primary-6));
}
.brand-caption {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.06em;
}
.sign-out-card {
  width: 100%;
  max-width: 416px;
  text-align: center;
}
.status-symbol {
  width: 72px;
  height: 72px;
  margin: 0 auto 30px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border-2);
  border-radius: 24px;
  background: var(--color-bg-2);
  color: rgb(var(--primary-6));
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.035);
}
.status-symbol.done {
  color: rgb(var(--success-6));
}
.status-symbol.failed {
  color: rgb(var(--danger-6));
  font-size: 32px;
}
.status-symbol svg {
  width: 32px;
  height: 32px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--color-fill-3);
  border-top-color: rgb(var(--primary-6));
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--color-text-3);
  margin: 0 0 12px;
}
h1 {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.03em;
  margin: 0 0 14px;
}
.description {
  color: var(--color-text-3);
  font-size: 14px;
  line-height: 1.8;
  margin: 0;
  min-height: 50px;
}
.steps {
  list-style: none;
  margin: 28px 0 22px;
  padding: 8px 22px;
  border: 1px solid var(--color-border-2);
  border-radius: 16px;
  background: var(--color-bg-2);
  text-align: left;
}
.steps li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 0;
  font-size: 13px;
}
.steps li + li {
  border-top: 1px solid var(--color-border-1);
}
.step-number {
  color: var(--color-text-4);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.step-state {
  margin-left: auto;
  color: var(--color-text-3);
  font-size: 12px;
}
.complete .step-state {
  color: rgb(var(--success-6));
}
.error {
  margin-bottom: 16px;
  text-align: left;
}
.footnote,
footer {
  font-size: 12px;
  color: var(--color-text-4);
}
footer {
  margin-top: 56px;
  letter-spacing: 0.06em;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation: none;
  }
}
@media (max-width: 480px) {
  .brand {
    top: 28px;
    left: 24px;
  }
  h1 {
    font-size: 27px;
  }
  .steps {
    padding: 8px 16px;
  }
}
</style>
