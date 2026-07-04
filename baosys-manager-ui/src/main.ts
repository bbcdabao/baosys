import { createPinia } from 'pinia'
import { createApp } from 'vue'

import { applyTheme } from '@/composables/useTheme'
import { i18n } from '@/i18n'
import { router } from '@/router'

import App from './App.vue'
import './assets/css/main.css'

const app = createApp(App)
const pinia = createPinia()
applyTheme()
app.use(pinia)
app.use(i18n)
app.use(router)
app.mount('#app')
