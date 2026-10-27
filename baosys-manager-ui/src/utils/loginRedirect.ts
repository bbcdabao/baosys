/** 所有子系统须配置相同的 Manager 公开来源，以便统一跳转到共享登录入口。 */
export const managerOrigin = new URL(import.meta.env.VITE_MANAGER_ORIGIN || window.location.origin)
  .origin
let navigating = false

export const goToManagerLogin = (returnUrl = window.location.href): void => {
  const login = new URL('/login', managerOrigin)
  if (
    navigating ||
    (window.location.origin === login.origin && window.location.pathname === login.pathname)
  ) {
    return
  }
  if (login.hostname !== window.location.hostname || login.protocol !== window.location.protocol) {
    throw new Error('Manager must use the same host and protocol as the subsystem')
  }
  navigating = true
  login.searchParams.set('redirect_url', returnUrl)
  window.location.assign(login.href)
}
