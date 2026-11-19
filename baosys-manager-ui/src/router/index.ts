import {
  IconUserGroup,
  IconUser,
  IconDesktop,
  IconSettings,
  IconTool
} from '@arco-design/web-vue/es/icon'
import type {} from '@baosys/common-ui/layout'
import { createRouter, createWebHashHistory } from 'vue-router'

import { useAuthStore } from '@/store/auth'

import { Message } from '@arco-design/web-vue'

// 兼容服务端、其他子系统及书签中的旧 History 地址，保留查询参数和页内锚点。
const base = import.meta.env.BASE_URL
if (window.location.pathname.startsWith(base) && window.location.pathname !== base) {
  const path = window.location.pathname.slice(base.length)
  window.history.replaceState(
    window.history.state,
    '',
    `${base}#/${path}${window.location.search}${window.location.hash}`
  )
}

export const router = createRouter({
  history: createWebHashHistory(base),
  routes: [
    {
      path: '/sign-out',
      name: 'sign-out',
      component: () => import('@/views/login/SignOutView.vue'),
      meta: { public: true, titleKey: 'signOut.title' }
    },
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
            menu: {
              icon: IconUser,
              order: 10,
              group: { key: 'user', titleKey: 'menu.user', icon: IconUserGroup }
            },
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
            menu: {
              icon: IconDesktop,
              order: 20,
              group: { key: 'user', titleKey: 'menu.user', icon: IconUserGroup }
            },
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
            menu: {
              icon: IconTool,
              order: 30,
              group: { key: 'system', titleKey: 'menu.system', icon: IconSettings }
            },
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
