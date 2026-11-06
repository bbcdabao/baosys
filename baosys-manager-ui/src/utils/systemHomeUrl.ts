/** Resolve a validated home path relative to its subsystem root. */
export const systemHomeUrl = (baseUrl: string, homePath: string): string => {
  const base = new URL(baseUrl)
  if (!homePath.startsWith('/') || homePath.startsWith('//') ||
      /[\\\x00-\x1f\x7f]|%0[0-9a-f]|%1[0-9a-f]|%7f|%5c|%2f|%25/i.test(homePath)) {
    throw new Error('Invalid subsystem home path')
  }
  const path = decodeURIComponent(homePath.split(/[?#]/, 1)[0])
  if (path.includes('//') || path.split('/').some(part => part === '.' || part === '..')) {
    throw new Error('Invalid subsystem home path')
  }
  const target = new URL(homePath.slice(1), base)
  if (!base.pathname.endsWith('/') || target.origin !== base.origin ||
      !target.pathname.startsWith(base.pathname)) throw new Error('Invalid subsystem home URL')
  return target.href
}
