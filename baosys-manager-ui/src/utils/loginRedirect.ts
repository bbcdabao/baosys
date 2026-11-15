/**
 * 其他子系统须配置相同的 Manager 完整 UI 基址，以便统一跳转到共享登录入口。
 */
export const managerUiBaseUrl = new URL(
  import.meta.env.VITE_MANAGER_UI_BASE_URL || import.meta.env.BASE_URL,
  window.location.origin
)
if (
  !['http:', 'https:'].includes(managerUiBaseUrl.protocol) ||
  managerUiBaseUrl.username ||
  managerUiBaseUrl.password ||
  managerUiBaseUrl.search ||
  managerUiBaseUrl.hash ||
  !managerUiBaseUrl.pathname.endsWith('/')
) {
  throw new Error('Invalid Manager UI base URL')
}
let navigating = false

export const goToManagerSignOut = (): void => {
  if (navigating) return
  const target = new URL('#/sign-out', managerUiBaseUrl)
  if (
    target.hostname !== window.location.hostname ||
    target.protocol !== window.location.protocol
  ) {
    throw new Error('Manager must use the same host and protocol as the subsystem')
  }
  window.location.assign(target.href)
  navigating = true
}

export const goToManagerLogin = (returnUrl = window.location.href): void => {
  const login = new URL('#/login', managerUiBaseUrl)
  if (
    navigating ||
    (window.location.origin === login.origin &&
      window.location.pathname === login.pathname &&
      window.location.hash.split('?')[0] === '#/login')
  ) {
    return
  }
  if (login.hostname !== window.location.hostname || login.protocol !== window.location.protocol) {
    throw new Error('Manager must use the same host and protocol as the subsystem')
  }
  navigating = true
  const target = new URL(returnUrl, window.location.origin)
  // Manager 的站点根入口不属于子系统路径，登录后使用账户默认系统。
  const isRootEntry = target.origin === login.origin && target.pathname === '/' &&
    (!target.hash || target.hash === '#/')
  if (!isRootEntry) {
    login.hash = `/login?${new URLSearchParams({ redirect_url: returnUrl })}`
  }
  window.location.assign(login.href)
}
