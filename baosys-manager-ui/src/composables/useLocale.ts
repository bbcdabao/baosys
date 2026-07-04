import enUS from '@arco-design/web-vue/es/locale/lang/en-us'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import { computed } from 'vue'

import {
  i18n,
  LOCALE_STORAGE_KEY,
  type AppLocale
} from '@/i18n'

export const useLocale = () => {
  const locale = computed<AppLocale>({
    get: () => i18n.global.locale.value as AppLocale,
    set: value => {
      // Vue I18n 与 Arco ConfigProvider 共用同一语言状态，避免组件文案不同步。
      i18n.global.locale.value = value
      localStorage.setItem(LOCALE_STORAGE_KEY, value)
      document.documentElement.lang = value
    }
  })
  const arcoLocale = computed(() => locale.value === 'zh-CN' ? zhCN : enUS)

  return {
    locale,
    arcoLocale
  }
}
