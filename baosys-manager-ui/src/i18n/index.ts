import { createAppI18n } from '@baosys/common-ui/i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'
export { LOCALE_STORAGE_KEY, type AppLocale } from '@baosys/common-ui/i18n'
export const i18n = createAppI18n({ 'zh-CN': zhCN, 'en-US': enUS })
