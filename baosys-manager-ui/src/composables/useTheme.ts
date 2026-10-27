import { computed, ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

const THEME_STORAGE_KEY = 'baosys-theme'
const savedTheme = localStorage.getItem(THEME_STORAGE_KEY)
const initialTheme: ThemeMode =
  savedTheme === 'light' || savedTheme === 'dark'
    ? savedTheme
    : window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
const theme = ref<ThemeMode>(initialTheme)

// Arco 通过 body 上的 arco-theme 属性切换整套 Design Token。
export const applyTheme = (mode: ThemeMode = theme.value): void => {
  theme.value = mode
  if (mode === 'dark') {
    document.body.setAttribute('arco-theme', 'dark')
  } else {
    document.body.removeAttribute('arco-theme')
  }
}

export const useTheme = () => {
  const isDark = computed(() => theme.value === 'dark')
  const toggleTheme = (): void => {
    const nextTheme: ThemeMode = isDark.value ? 'light' : 'dark'
    localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
    applyTheme(nextTheme)
  }

  return {
    theme,
    isDark,
    toggleTheme
  }
}
