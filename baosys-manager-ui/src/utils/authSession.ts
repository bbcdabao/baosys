const CSRF_COOKIE_NAME =
  import.meta.env.VITE_CSRF_COOKIE_NAME || 'BAOSYS_CSRF_TOKEN'

// 这里只保存 CSRF Token；用户信息始终只保留在 Pinia 内存中。
export const CSRF_TOKEN_STORAGE_KEY = 'baosys-csrf-token'

export const saveCsrfToken = (csrfToken: string): void => {
  sessionStorage.setItem(CSRF_TOKEN_STORAGE_KEY, csrfToken)
}

export const syncCsrfTokenFromCookie = (): void => {
  const prefix = `${encodeURIComponent(CSRF_COOKIE_NAME)}=`
  const csrfCookie = document.cookie
    .split('; ')
    .find(cookie => cookie.startsWith(prefix))
  if (csrfCookie) {
    saveCsrfToken(decodeURIComponent(csrfCookie.slice(prefix.length)))
  }
}
