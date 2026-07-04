import {
  createRouter,
  createWebHistory,
  type RouteLocationRaw
} from 'vue-router'

import { useAuthStore } from '@/store/auth'

const loginRedirect = (redirect: string): RouteLocationRaw => ({
  name: 'login',
  // 默认页无需携带冗余 redirect，保持登录 URL 简洁且可复用。
  query: redirect === '/users-management' ? {} : { redirect },
  replace: true
})

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true, titleKey: 'login.title' }
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        {
          path: '',
          redirect: { name: 'user-management' }
        },
        {
          path: 'users-management',
          name: 'user-management',
          component: () => import('@/views/user/UserManagementView.vue'),
          meta: {
            menuTitleKey: 'menu.user',
            titleKey: 'menu.userManagement'
          }
        },
        {
          path: 'login-sessions',
          name: 'login-management',
          component: () => import('@/views/user/LoginManagementView.vue'),
          meta: {
            menuTitleKey: 'menu.user',
            titleKey: 'menu.loginManagement'
          }
        },
        {
          path: 'system-config',
          name: 'system-config',
          component: () => import('@/views/system/SystemConfigView.vue'),
          meta: {
            menuTitleKey: 'menu.system',
            titleKey: 'menu.systemConfig'
          }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: { name: 'user-management' }
    }
  ]
})

router.beforeEach(async to => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    // 已登录用户不再停留于登录页，避免重复提交凭据。
    return authStore.isAuthenticated
      ? { name: 'user-management', replace: true }
      : true
  }

  // 用户快照不落盘，每次刷新后由守卫恢复，确保后台布局拿到可信用户信息。
  if (!authStore.isAuthenticated) {
    try {
      await authStore.restoreSession()
    } catch {
      return loginRedirect(to.fullPath)
    }
  }

  return true
})
