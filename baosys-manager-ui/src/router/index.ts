import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/store/auth'

import { Message } from '@arco-design/web-vue'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
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
          path: 'authorized-systems',
          name: 'authorized-systems',
          component: () => import('@/views/system/AuthorizedSystemsView.vue'),
          meta: { titleKey: 'user.authorizedSystems' }
        },
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
            titleKey: 'menu.userManagement',
            requiredPermission: 'MGR:user:list'
          }
        },
        {
          path: 'login-sessions',
          name: 'login-management',
          component: () => import('@/views/user/LoginManagementView.vue'),
          meta: {
            menuTitleKey: 'menu.user',
            titleKey: 'menu.loginManagement',
            requiredPermission: 'MGR:user:session:list'
          }
        },
        {
          path: 'system-config',
          name: 'system-config',
          component: () => import('@/views/system/SystemConfigView.vue'),
          meta: {
            menuTitleKey: 'menu.system',
            titleKey: 'menu.systemConfig',
            requiredPermission: 'MGR'
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

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    return true
  }

  // 用户快照不落盘，每次刷新后由守卫恢复，确保后台布局拿到可信用户信息。
  if (!authStore.isAuthenticated) {
    try {
      await authStore.restoreSession()
    } catch (error) {
      // 明确失效由认证拦截器发出 go-login；网络/服务故障不能当成退出。
      Message.error(error instanceof Error ? error.message : '加载登录状态失败，请重试')
      return false
    }
  }

  // 管理页面同时要求系统入口权限和页面权限；否则展示可访问的系统。
  if (to.name !== 'authorized-systems') {
    const permissions = authStore.currentUser?.permissions || []
    if (
      !permissions.includes('MGR') ||
      (to.meta.requiredPermission && !permissions.includes(String(to.meta.requiredPermission)))
    ) {
      return { name: 'authorized-systems', replace: true }
    }
  }
  return true
})
