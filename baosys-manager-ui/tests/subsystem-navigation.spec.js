import { test, expect } from '@playwright/test'

const prefix = '/api/baosys-manager/v1'
const systems = [
  { systemType: 'MGR', name: { 'zh-CN': '后台管理', 'en-US': 'Management' },
    uiBaseUrl: 'http://localhost:5178/baosys-manager/', homePath: '/users-management',
    permissions: ['list', 'add', 'update'].map(action => ({ code: `MGR:user:${action}`, name: { 'zh-CN': action } })) },
  { systemType: 'CAT', name: { 'zh-CN': '拨测平台', 'en-US': 'Automated' },
    uiBaseUrl: 'http://localhost:8081/baosys-xxxx/', homePath: '/', permissions: [] }
]

async function setup(page, permissions) {
  const state = { saved: [], loggedOut: false }
  await page.addInitScript(() => localStorage.setItem('baosys-locale', 'zh-CN'))
  await page.route('**/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF', token: 'test' } }))
  await page.route('**/api/baosys-manager/**', route => {
    const req = route.request(), path = new URL(req.url()).pathname
    if (path.endsWith("/csrf")) return route.fallback()
    let data
    if (path === `${prefix}/users/me`) data = { userId: 1, username: 'test', displayName: 'test', permissions }
    else if (path === `${prefix}/systems`) data = systems
    else if (path === `${prefix}/browser-auth/logout`) { state.loggedOut = true; data = null }
    else if (path === `${prefix}/users` && req.method() !== 'GET') {
      data = { id: 2, ...req.postDataJSON() }; state.saved.push(data)
    } else if (path === `${prefix}/users`) data = {
      items: [{ id: 2, name: 'existing', permissions: ['MGR'], defaultSystemType: 'CAT', enabled: true, status: false }],
      total: 1, page: 0, size: 20, totalPages: 1
    }
    else return route.fulfill({ status: 404 })
    return route.fulfill({ json: { code: 'SUCCESS', data } })
  })
  return state
}

test('CAT-only account reaches fallback and can enter CAT or sign out', async ({ page }) => {
  const state = await setup(page, ['CAT'])
  await page.goto('/baosys-manager/users-management')
  await expect(page).toHaveURL(/\/authorized-systems$/)
  await expect(page.getByRole('link', { name: '拨测平台' })).toHaveAttribute('href', 'http://localhost:8081/baosys-xxxx/')
  await expect(page.getByRole('link', { name: '后台管理', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(state.loggedOut).toBe(true)
})

test('account with no permissions stays on fallback without a redirect loop', async ({ page }) => {
  await setup(page, [])
  await page.goto('/baosys-manager/authorized-systems')
  await expect(page.getByText('当前账户尚未分配子系统权限')).toBeVisible()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(page).toHaveURL(/\/authorized-systems$/)
})

test('create requires a default subsystem and edit preserves and changes it', async ({ page }) => {
  const state = await setup(page, ['MGR', 'MGR:user:list', 'MGR:user:add', 'MGR:user:update'])
  await page.goto('/baosys-manager/users-management')
  await page.getByRole('button', { name: '新建', exact: true }).click()
  const drawer = page.locator('.arco-drawer')
  await drawer.locator('input').nth(0).fill('created')
  await drawer.locator('input[type=password]').fill('secure123')
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer.locator('.arco-form-item-message')).toContainText('请选择默认登录子系统')
  expect(state.saved).toHaveLength(0)
  await drawer.locator('.arco-form-item').filter({ hasText: '默认登录子系统' }).locator('.arco-select-view').click()
  await page.getByText('拨测平台 (CAT)', { exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toBeHidden()
  expect(state.saved[0]).toMatchObject({ name: 'created', defaultSystemType: 'CAT', permissions: [] })
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const selector = drawer.locator('.arco-form-item').filter({ hasText: '默认登录子系统' }).locator('.arco-select-view')
  await expect(selector).toContainText('拨测平台 (CAT)')
  await selector.click()
  await page.getByText('后台管理 (MGR)', { exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toBeHidden()
  expect(state.saved[1]).toMatchObject({ id: 2, defaultSystemType: 'MGR' })
})

test('shared login forwards the complete deep link and follows server navigation', async ({ page }) => {
  const target = 'http://localhost:8081/baosys-xxxx/xxxx/zzzz/yyy?task=123#detail'
  await setup(page, ['CAT'])
  let submitted
  await page.route(`**${prefix}/browser-auth/login`, route => {
    submitted = route.request().postDataJSON()
    return route.fulfill({ json: { code: 'SUCCESS', data: { redirectUrl: target } } })
  })
  await page.route('http://localhost:8081/baosys-xxxx/**', route => route.fulfill({ contentType: 'text/html', body: '<p>CAT destination</p>' }))
  await page.goto(`/baosys-manager/login?redirect_url=${encodeURIComponent(target)}`)
  await page.locator('input[autocomplete=username]').fill('cat-user')
  await page.locator('input[autocomplete=current-password]').fill('secure123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(target)
  expect(submitted.redirectUrl).toBe(target)
})


test('system home URLs preserve prefixes and reject traversal', async ({ page }) => {
  await page.goto('/baosys-manager/tests/auth-harness.html')
  const result = await page.evaluate(async () => {
    const { systemHomeUrl } = await import('/baosys-manager/src/utils/systemHomeUrl.ts')
    const base = 'https://example.com/baosys-manager/'
    const rejected = ['/../tasks', '/x/../tasks', '/%2e%2e/tasks', '//evil.test/', '/%252e/tasks']
      .every(path => { try { systemHomeUrl(base, path); return false } catch { return true } })
    return { home: systemHomeUrl(base, '/users-management?q=1#detail'), root: systemHomeUrl(base, '/'), rejected }
  })
  expect(result).toEqual({ home: 'https://example.com/baosys-manager/users-management?q=1#detail',
    root: 'https://example.com/baosys-manager/', rejected: true })
})
