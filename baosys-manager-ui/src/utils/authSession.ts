const CSRF_COOKIE_NAME =
  import.meta.env.VITE_CSRF_COOKIE_NAME || 'BAOSYS_CSRF_TOKEN'

// 这里只保存 CSRF Token；用户信息不落盘，避免本地旧快照被误当成认证依据。
export const CSRF_TOKEN_STORAGE_KEY = 'baosys-csrf-token'

export const saveCsrfToken = (csrfToken: string): void => {
  sessionStorage.setItem(CSRF_TOKEN_STORAGE_KEY, csrfToken)
}

export const syncCsrfTokenFromCookie = (): void => {
  // Cookie 由后端写入；同步后由请求拦截器放入双提交校验头。
  const prefix = `${encodeURIComponent(CSRF_COOKIE_NAME)}=`
  const csrfCookie = document.cookie
    .split('; ')
    .find(cookie => cookie.startsWith(prefix))
  if (csrfCookie) {
    saveCsrfToken(decodeURIComponent(csrfCookie.slice(prefix.length)))
  }
}
