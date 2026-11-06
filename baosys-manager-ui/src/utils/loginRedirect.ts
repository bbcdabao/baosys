/**
 * 其他子系统须配置相同的 Manager 完整 UI 基址，以便统一跳转到共享登录入口。
 */
export const managerUiBaseUrl = new URL(
  import.meta.env.VITE_MANAGER_UI_BASE_URL || import.meta.env.BASE_URL, window.location.origin
)
if (!['http:', 'https:'].includes(managerUiBaseUrl.protocol) ||
    managerUiBaseUrl.username || managerUiBaseUrl.password || managerUiBaseUrl.search ||
    managerUiBaseUrl.hash || !managerUiBaseUrl.pathname.endsWith('/')) {
  throw new Error('Invalid Manager UI base URL')
}
let navigating = false

export const goToManagerLogin = (returnUrl = window.location.href): void => {
  const login = new URL('login', managerUiBaseUrl)
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
