import { computed, ref } from 'vue'
import { readPreference, writePreference } from './storage'

export type ThemeMode = 'light' | 'dark'
export const THEME_STORAGE_KEY = 'baosys-theme'
const preferredTheme = (): ThemeMode => {
  const saved = readPreference(THEME_STORAGE_KEY)
  return saved === 'light' || saved === 'dark'
    ? saved
    : window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
}
const theme = ref<ThemeMode>(preferredTheme())

export const applyTheme = (mode: ThemeMode = theme.value): void => {
  theme.value = mode
  if (mode === 'dark') document.body.setAttribute('arco-theme', 'dark')
  else document.body.removeAttribute('arco-theme')
}

// Storage events synchronize other documents on this origin without echo writes.
window.addEventListener('storage', (event) => {
  if (event.key === THEME_STORAGE_KEY || event.key === null) applyTheme(preferredTheme())
})

export const useTheme = () => ({
  theme,
  isDark: computed(() => theme.value === 'dark'),
  toggleTheme: (): void => {
    const next = theme.value === 'dark' ? 'light' : 'dark'
    writePreference(THEME_STORAGE_KEY, next)
    applyTheme(next)
  }
})
