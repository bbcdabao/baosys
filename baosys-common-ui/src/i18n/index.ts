import { computed } from 'vue'
import { createI18n, useI18n } from 'vue-i18n'
import enUS from '@arco-design/web-vue/es/locale/lang/en-us'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import { readPreference, writePreference } from '../preferences/storage'

export type AppLocale = 'zh-CN' | 'en-US'
export const LOCALE_STORAGE_KEY = 'baosys-locale'
export const localeOptions = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' }
]
export const isAppLocale = (value: unknown): value is AppLocale =>
  value === 'zh-CN' || value === 'en-US'
const commonMessages = {
  'zh-CN': {
    expandSidebar: '展开菜单',
    collapseSidebar: '收起菜单',
    language: '语言',
    light: '亮色模式',
    dark: '暗黑模式',
    fullscreen: '进入全屏',
    exitFullscreen: '退出全屏',
    fullscreenUnavailable: '无法切换全屏，请检查浏览器是否支持或允许全屏',
    logout: '退出登录'
  },
  'en-US': {
    expandSidebar: 'Expand sidebar',
    collapseSidebar: 'Collapse sidebar',
    language: 'Language',
    light: 'Light mode',
    dark: 'Dark mode',
    fullscreen: 'Enter fullscreen',
    exitFullscreen: 'Exit fullscreen',
    fullscreenUnavailable:
      'Unable to toggle fullscreen. Check whether your browser supports and allows it.',
    logout: 'Sign out'
  }
}
type Messages = Record<string, any>
export function createAppI18n(
  messages: Record<AppLocale, Messages>,
  legacyLocale?: () => AppLocale | undefined
) {
  const saved = readPreference(LOCALE_STORAGE_KEY)
  const locale = isAppLocale(saved)
    ? saved
    : (legacyLocale?.() ?? (navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'))
  document.documentElement.lang = locale
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'zh-CN',
    messages: {
      'zh-CN': {
        ...messages['zh-CN'],
        common: { ...commonMessages['zh-CN'], ...messages['zh-CN'].common }
      },
      'en-US': {
        ...messages['en-US'],
        common: { ...commonMessages['en-US'], ...messages['en-US'].common }
      }
    }
  })
}
/** Call in setup, using the application's installed i18n instance. */
export function useLocale() {
  const { locale: currentLocale } = useI18n({ useScope: 'global' })
  const locale = computed<AppLocale>({
    get: () => (isAppLocale(currentLocale.value) ? currentLocale.value : 'zh-CN'),
    set: (value) => {
      if (!isAppLocale(value)) return
      currentLocale.value = value
      writePreference(LOCALE_STORAGE_KEY, value)
      document.documentElement.lang = value
    }
  })
  const arcoLocale = computed(() => (locale.value === 'zh-CN' ? zhCN : enUS))
  return { locale, arcoLocale }
}
