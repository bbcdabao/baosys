import { createI18n } from 'vue-i18n'

import enUS from '@/i18n/locales/en-US'
import zhCN from '@/i18n/locales/zh-CN'

export type AppLocale = 'zh-CN' | 'en-US'

export const LOCALE_STORAGE_KEY = 'baosys-locale'

const resolveInitialLocale = (): AppLocale => {
  const saved = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (saved === 'zh-CN' || saved === 'en-US') {
    return saved
  }
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})
