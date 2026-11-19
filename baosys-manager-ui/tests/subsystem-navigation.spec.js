import { test, expect } from '@playwright/test'

async function expectAlignedScrollAreas(page) {
  await expect
    .poll(() =>
      page.evaluate(() => {
        const left = document.querySelector('.sidebar-menu .arco-menu-inner')
        const right = document.querySelector('.admin-content')
        const a = left.getBoundingClientRect(),
          b = right.getBoundingClientRect()
        return Math.abs(a.top - b.top) < 1 && Math.abs(a.bottom - b.bottom) < 1
      })
    )
    .toBe(true)
  const geometry = await page.evaluate(() => {
    const sidebar = document.querySelector('.admin-sider')
    const toggle = sidebar.querySelector('.sidebar-toggle').getBoundingClientRect()
    const icons = [...sidebar.querySelectorAll('.arco-menu-icon')].filter(
      (el) => el.getBoundingClientRect().width > 0
    )
    const inner = sidebar.querySelector('.arco-menu-inner')
    return {
      overflow: inner.scrollWidth > inner.clientWidth,
      usableRows: [
        ...sidebar.querySelectorAll(
          '.arco-menu-item, .arco-menu-inline-header, .arco-menu-pop-header'
        )
      ].every((el) => {
        const height = el.getBoundingClientRect().height
        return height === 0 || height >= 40
      }),
      aligned: icons.every((el) => {
        const box = el.getBoundingClientRect()
        return Math.abs(box.x + box.width / 2 - toggle.x - toggle.width / 2) < 1
      })
    }
  })
  expect(geometry).toEqual({ overflow: false, usableRows: true, aligned: true })
}

const prefix = '/api/baosys-manager/v1'
const systems = [
  {
    systemType: 'MGR',
    name: { 'zh-CN': '后台管理', 'en-US': 'Management' },
    uiBaseUrl: 'http://localhost:5178/baosys-manager/',
    homePath: '/users-management',
    permissions: ['list', 'add', 'update'].map((action) => ({
      code: `MGR:user:${action}`,
      name: { 'zh-CN': action }
    }))
  },
  {
    systemType: 'CAT',
    name: { 'zh-CN': '拨测平台', 'en-US': 'Automated' },
    uiBaseUrl: 'http://localhost:8081/baosys-xxxx/',
    homePath: '/',
    permissions: []
  }
]

async function setup(page, permissions, username = 'existing') {
  const state = { saved: [], loggedOut: false }
  await page.addInitScript(() => localStorage.setItem('baosys-locale', 'zh-CN'))
  await page.route('**/csrf', (route) =>
    route.fulfill({ json: { headerName: 'X-CSRF', token: 'test' } })
  )
  await page.route('**/api/baosys-manager/**', (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname
    if (path.endsWith('/csrf')) return route.fallback()
    let data
    if (path === `${prefix}/users/me`)
      data = { userId: 1, username: 'test', displayName: 'test', permissions }
    else if (path === `${prefix}/systems`) data = systems
    else if (path === `${prefix}/browser-auth/logout`) {
      state.loggedOut = true
      data = null
    } else if (path === `${prefix}/users` && req.method() !== 'GET') {
      data = { id: 2, ...req.postDataJSON() }
      state.saved.push(data)
    } else if (path === `${prefix}/users`)
      data = {
        items: [
          {
            id: 2,
            name: username,
            permissions: ['MGR'],
            defaultSystemType: 'CAT',
            enabled: true,
            status: false
          }
        ],
        total: 1,
        page: 0,
        size: 20,
        totalPages: 1
      }
    else return route.fulfill({ status: 404 })
    return route.fulfill({ json: { code: 'SUCCESS', data } })
  })
  return state
}

test('shared layout preserves navigation, breadcrumbs and header controls', async ({ page }) => {
  await setup(page, ['MGR', 'MGR:user:list'])
  await page.addInitScript(() => localStorage.setItem('baosys-theme', 'light'))
  await page.goto('/baosys-manager/#/users-management')
  const sidebar = page.locator('.admin-sider')
  const header = page.locator('.admin-header')
  await expect(sidebar.locator('.arco-menu-item.arco-menu-selected')).toContainText('用户管理')
  await expect(sidebar).not.toContainText('登录管理')
  await expect(header.locator('.arco-breadcrumb-item')).toHaveText([
    'baosys',
    '后台管理',
    '用户管理'
  ])
  await expect(header.locator('.arco-breadcrumb-item').last()).toHaveText('用户管理')
  await expect(sidebar).toHaveCSS('width', '232px')
  await expectAlignedScrollAreas(page)
  await expect(sidebar.locator('.arco-layout-sider-trigger')).toHaveCount(0)
  await expect(sidebar.getByRole('button', { name: '收起菜单' })).toHaveAttribute(
    'aria-expanded',
    'true'
  )
  expect((await sidebar.locator('.sidebar-toggle').boundingBox()).y).toBeLessThan(64)
  const lightScrollbar = await page
    .locator('.admin-content')
    .evaluate((el) => getComputedStyle(el, '::-webkit-scrollbar-thumb').backgroundColor)
  await sidebar.locator('.sidebar-toggle').click()
  await expect(sidebar).toHaveCSS('width', '64px')
  await expectAlignedScrollAreas(page)
  await expect(sidebar.getByRole('button', { name: '展开菜单' })).toHaveAttribute(
    'aria-expanded',
    'false'
  )
  await sidebar.locator('.sidebar-toggle').click()
  await expect(sidebar).toHaveCSS('width', '232px')
  await expectAlignedScrollAreas(page)
  await header
    .locator('button')
    .filter({ has: page.locator('.arco-icon-moon-fill') })
    .click()
  await expect(page.locator('body')).toHaveAttribute('arco-theme', 'dark')
  await expect
    .poll(() =>
      page
        .locator('.admin-content')
        .evaluate((el) => getComputedStyle(el, '::-webkit-scrollbar-thumb').backgroundColor)
    )
    .not.toBe(lightScrollbar)
  await header.getByRole('button', { name: '进入全屏', exact: true }).click()
  await expect(header.getByRole('button', { name: '退出全屏', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true'
  )
  await header.getByRole('button', { name: '退出全屏', exact: true }).click()
  await expect(header.getByRole('button', { name: '进入全屏', exact: true })).toHaveAttribute(
    'aria-pressed',
    'false'
  )
  await header.locator('.locale-select').click()
  await page.getByText('English', { exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en-US')
  await expect(header.getByRole('button', { name: 'Sign out', exact: true })).toBeVisible()
  await header.getByRole('button', { name: 'Authorized subsystems', exact: true }).click()
  await expect(page).toHaveURL(/#\/authorized-systems$/)
  await expect(sidebar).not.toContainText('Authorized')
  await expect(header.locator('.arco-breadcrumb-item')).toHaveText([
    'baosys',
    'Management',
    'Authorized subsystems'
  ])
})

test('preferences persist and synchronize between same-origin pages', async ({ page, context }) => {
  await setup(page, ['MGR', 'MGR:user:list'])
  await page.goto('/baosys-manager/#/authorized-systems')
  await page.evaluate(() => {
    localStorage.setItem('baosys-theme', 'light')
    localStorage.setItem('baosys-fullscreen', 'false')
  })
  await page.reload()
  const second = await context.newPage()
  await setup(second, ['MGR', 'MGR:user:list'])
  await second.goto('/baosys-manager/#/authorized-systems')
  await page.locator('.admin-header').getByRole('button', { name: '暗黑模式', exact: true }).click()
  await expect(second.locator('body')).toHaveAttribute('arco-theme', 'dark')
  await second.reload()
  await expect(second.locator('body')).toHaveAttribute('arco-theme', 'dark')
  await second.close()
  const fullscreenButton = page.locator('[data-fullscreen-toggle]')
  await fullscreenButton.click()
  await expect(fullscreenButton).toHaveAttribute('aria-pressed', 'true')
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('baosys-fullscreen')))
    .toBe('true')
  await page.reload()
  await expect(fullscreenButton).toHaveAttribute('aria-pressed', 'false')
  expect(await page.evaluate(() => localStorage.getItem('baosys-fullscreen'))).toBe('true')
  await page.locator('.brand').click()
  await expect(fullscreenButton).toHaveAttribute('aria-pressed', 'true')
  await fullscreenButton.click()
  await expect(fullscreenButton).toHaveAttribute('aria-pressed', 'false')
  expect(await page.evaluate(() => localStorage.getItem('baosys-fullscreen'))).toBe('false')
})

test('CAT-only account reaches fallback and can enter CAT or sign out', async ({ page }) => {
  const state = await setup(page, ['CAT'])
  await page.goto('/baosys-manager/users-management')
  await expect(page).toHaveURL(/\/baosys-manager\/#\/authorized-systems$/)
  await expect(page.getByRole('link', { name: '拨测平台' })).toHaveAttribute(
    'href',
    'http://localhost:8081/baosys-xxxx/'
  )
  await expect(page.getByRole('link', { name: '后台管理', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/baosys-manager\/#\/login$/)
  expect(state.loggedOut).toBe(true)
})

test('account with no permissions stays on fallback without a redirect loop', async ({ page }) => {
  await setup(page, [])
  await page.goto('/baosys-manager/#/authorized-systems')
  await page.reload()
  await expect(page.getByText('当前账户尚未分配子系统权限')).toBeVisible()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(page).toHaveURL(/\/baosys-manager\/#\/authorized-systems$/)
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
  await drawer
    .locator('.arco-form-item')
    .filter({ hasText: '默认登录子系统' })
    .locator('.arco-select-view')
    .click()
  await page.getByText('拨测平台 (CAT)', { exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toBeHidden()
  expect(state.saved[0]).toMatchObject({
    name: 'created',
    defaultSystemType: 'CAT',
    permissions: []
  })
  await page.getByRole('button', { name: '详情: existing', exact: true }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const selector = drawer
    .locator('.arco-form-item')
    .filter({ hasText: '默认登录子系统' })
    .locator('.arco-select-view')
  await expect(selector).toContainText('拨测平台 (CAT)')
  await selector.click()
  await page.getByText('后台管理 (MGR)', { exact: true }).click()
  await drawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(drawer).toBeHidden()
  expect(state.saved[1]).toMatchObject({ id: 2, defaultSystemType: 'MGR' })
})

for (const loginPath of ['/baosys-manager/login', '/baosys-manager/#/login']) {
  test(`shared login preserves the complete deep link from ${loginPath}`, async ({ page }) => {
    const target = 'http://localhost:8081/baosys-xxxx/xxxx/zzzz/yyy?task=123#detail'
    await setup(page, ['CAT'])
    let submitted
    await page.route(`**${prefix}/browser-auth/login`, (route) => {
      submitted = route.request().postDataJSON()
      return route.fulfill({ json: { code: 'SUCCESS', data: { redirectUrl: target } } })
    })
    await page.route('http://localhost:8081/baosys-xxxx/**', (route) =>
      route.fulfill({ contentType: 'text/html', body: '<p>CAT destination</p>' })
    )
    await page.goto(`${loginPath}?redirect_url=${encodeURIComponent(target)}`)
    await expect(page).toHaveURL(
      `/baosys-manager/#/login?redirect_url=${encodeURIComponent(target)}`
    )
    await page.locator('input[autocomplete=username]').fill('cat-user')
    await page.locator('input[autocomplete=current-password]').fill('secure123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(target)
    expect(submitted.redirectUrl).toBe(target)
  })
}

test('login navigation puts the return URL inside the hash query', async ({ page }) => {
  await setup(page, [])
  await page.goto('/baosys-manager/#/authorized-systems')
  const target = page.url()
  await page.evaluate(async () => {
    const { goToManagerLogin } = await import('/baosys-manager/src/utils/loginRedirect.ts')
    goToManagerLogin()
  })
  await expect(page).toHaveURL(
    `/baosys-manager/#/login?${new URLSearchParams({ redirect_url: target })}`
  )
  await page.evaluate(async () => {
    const { goToManagerLogin } = await import('/baosys-manager/src/utils/loginRedirect.ts')
    goToManagerLogin()
  })
  await expect(page).toHaveURL(
    `/baosys-manager/#/login?${new URLSearchParams({ redirect_url: target })}`
  )
})

for (const entry of ['/', '/#/']) {
  test(`root entry ${entry} omits the login return URL`, async ({ page }) => {
    await page.goto('/baosys-manager/tests/auth-harness.html')
    await page.evaluate(async (path) => {
      const { goToManagerLogin } = await import('/baosys-manager/src/utils/loginRedirect.ts')
      window.history.replaceState(null, '', path)
      goToManagerLogin()
    }, entry)
    await expect(page).toHaveURL('/baosys-manager/#/login')
  })
}

test('another subsystem root preserves the login return URL', async ({ page }) => {
  await page.goto('/baosys-manager/tests/auth-harness.html')
  const target = 'http://localhost:8081/#/'
  await page.evaluate(async (url) => {
    const { goToManagerLogin } = await import('/baosys-manager/src/utils/loginRedirect.ts')
    goToManagerLogin(url)
  }, target)
  await expect(page).toHaveURL(
    `/baosys-manager/#/login?${new URLSearchParams({ redirect_url: target })}`
  )
})

test('system home URLs preserve prefixes and reject traversal', async ({ page }) => {
  await page.goto('/baosys-manager/tests/auth-harness.html')
  const result = await page.evaluate(async () => {
    const { systemHomeUrl } = await import('/baosys-manager/src/utils/systemHomeUrl.ts')
    const base = 'https://example.com/baosys-manager/'
    const rejected = [
      '/../tasks',
      '/x/../tasks',
      '/%2e%2e/tasks',
      '//evil.test/',
      '/%252e/tasks'
    ].every((path) => {
      try {
        systemHomeUrl(base, path)
        return false
      } catch {
        return true
      }
    })
    return {
      home: systemHomeUrl(base, '/users-management?q=1#detail'),
      root: systemHomeUrl(base, '/'),
      rejected
    }
  })
  expect(result).toEqual({
    home: 'https://example.com/baosys-manager/users-management?q=1#detail',
    root: 'https://example.com/baosys-manager/',
    rejected: true
  })
})

for (const width of [320, 768, 1440]) {
  test('compact tables and full details at width ' + width, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const username = '用户名'.repeat(10) + '测试'
    await setup(
      page,
      ['MGR', 'MGR:user:list', 'MGR:user:session:list', 'MGR:user:session:kick'],
      username
    )
    const longClient = 'client-'.repeat(150)
    let kicked = false
    await page.route('**/api/baosys-manager/v1/login-sessions*', (route) =>
      route.fulfill({
        json: {
          code: 'SUCCESS',
          data: {
            items: [
              {
                id: 9,
                userId: 2,
                userName: username,
                permissions: ['MGR'],
                loginClient: longClient,
                loginAt: '2026-11-16T00:00:00Z',
                expiresAt: '2026-11-17T00:00:00Z'
              }
            ],
            total: 1
          }
        }
      })
    )
    await page.route('**/users/2/login-clients/9', (route) => {
      kicked = true
      return route.fulfill({ json: { code: 'SUCCESS', data: null } })
    })
    await page.goto('/baosys-manager/#/users-management')
    const table = page.locator('.management-table')
    await expect(table.locator('th')).toHaveText(['用户名称', '平台类型', '账号状态'])
    const fits = async () => {
      expect(await table.evaluate((el) => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
      expect(
        await page.locator('.admin-content').evaluate((el) => el.scrollWidth <= el.clientWidth + 1)
      ).toBe(true)
      for (const el of await table.locator('.arco-table-content, .arco-table-body').all()) {
        expect(await el.evaluate((e) => e.scrollWidth <= e.clientWidth + 1)).toBe(true)
      }
    }
    await expect
      .poll(async () =>
        page.locator('.admin-sider').evaluate((el) => el.getBoundingClientRect().width)
      )
      .toBe(width < 992 ? 64 : 232)
    await fits()
    await page.getByRole('button', { name: `详情: ${username}`, exact: true }).click()
    await expect(page.locator('.arco-drawer')).toContainText('默认登录子系统')
    await page
      .locator('.arco-drawer')
      .getByRole('button', { name: '登录管理', exact: true })
      .click()
    await expect(table.locator('th')).toHaveText(['用户名称', '登录时间', '过期时间'])
    await fits()
    await page.getByRole('button', { name: `详情: ${username}`, exact: true }).click()
    const drawer = page.locator('.arco-drawer')
    await expect(drawer).toContainText(longClient)
    expect(
      await drawer
        .locator('.arco-drawer-body')
        .evaluate((el) => el.scrollWidth <= el.clientWidth + 1)
    ).toBe(true)
    await drawer.getByRole('button', { name: '踢出', exact: true }).click()
    await page
      .locator('.arco-popconfirm')
      .getByRole('button', { name: '确定', exact: true })
      .click()
    await expect(drawer).toBeHidden()
    expect(kicked).toBe(true)
  })
}

test('authorized tree groups only granted permissions and preserves system links', async ({
  page
}) => {
  await setup(page, ['MGR', 'MGR:user:list', 'CAT'])
  await page.goto('/baosys-manager/#/authorized-systems')
  const tree = page.locator('.authorized-tree')
  await expect(tree.getByRole('link', { name: '后台管理', exact: true })).toHaveAttribute(
    'href',
    'http://localhost:5178/baosys-manager/users-management'
  )
  await expect(tree.getByRole('link', { name: '拨测平台', exact: true })).toBeVisible()
  await expect(tree.locator('.permission-node')).toHaveCount(1)
  await expect(tree.locator('.permission-node')).toContainText('MGR:user:list')
  await expect(tree.locator('.system-node').filter({ hasText: '拨测平台' })).toContainText(
    '仅子系统入口权限'
  )
  await expect(
    page
      .locator('.admin-header')
      .getByRole('button', { name: '已授权子系统', exact: true })
      .locator('svg')
  ).toBeVisible()
  const branch = tree
    .locator('.arco-tree-node')
    .filter({ has: page.getByRole('link', { name: '后台管理', exact: true }) })
  await branch.locator('.arco-tree-node-switcher').click()
  await expect(tree.locator('.permission-node')).toBeHidden()
  await branch.locator('.arco-tree-node-switcher').click()
  await expect(tree.locator('.permission-node')).toBeVisible()
})

test('fixed scrollbar widths survive extreme viewports, collapse and hover', async ({ page }) => {
  await setup(page, ['MGR', 'MGR:user:list'])
  await page.goto('/baosys-manager/#/users-management')
  await expect(page.locator('.admin-content')).toBeVisible()
  // Force both regions to overflow independently of backend data volume.
  await page.evaluate(() => {
    for (const selector of ['.sidebar-menu .arco-menu-inner', '.admin-content']) {
      const spacer = document.createElement('div')
      spacer.style.cssText = 'height: 3000px; width: 1px; flex-shrink: 0'
      document.querySelector(selector).appendChild(spacer)
    }
  })
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 320, height: 320 },
    { width: 1024, height: 180 }
  ]) {
    await page.setViewportSize(viewport)
    // Deliver matchMedia change events before manually toggling the sidebar.
    await page.evaluate(
      () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    )
    for (const collapsed of [false, true]) {
      const toggle = page.locator('.sidebar-toggle')
      if ((await toggle.getAttribute('aria-expanded')) !== String(!collapsed)) await toggle.click()
      await expect(page.locator('.admin-sider')).toHaveCSS('width', collapsed ? '64px' : '232px')
      for (const dark of [false, true]) {
        await page.evaluate((dark) => {
          if (dark) document.body.setAttribute('arco-theme', 'dark')
          else document.body.removeAttribute('arco-theme')
        }, dark)
        for (const selector of ['.sidebar-menu .arco-menu-inner', '.admin-content']) {
          const area = page.locator(selector)
          await page.mouse.move(0, 0)
          const measure = () =>
            area.evaluate((el) => {
              const style = getComputedStyle(el)
              const thumb = getComputedStyle(el, '::-webkit-scrollbar-thumb')
              return {
                track:
                  el.offsetWidth -
                  el.clientWidth -
                  parseFloat(style.borderLeftWidth) -
                  parseFloat(style.borderRightWidth),
                cssWidth: getComputedStyle(el, '::-webkit-scrollbar').width,
                thumbBorders: [thumb.borderLeftWidth, thumb.borderRightWidth],
                overflowing: el.scrollHeight > el.clientHeight,
                hasHeight: el.clientHeight > 0
              }
            })
          const before = await measure()
          expect(before).toEqual({
            track: 8,
            cssWidth: '8px',
            thumbBorders: ['2px', '2px'],
            overflowing: true,
            hasHeight: true
          })
          const box = await area.boundingBox()
          await page.mouse.move(box.x + box.width - 4, box.y + 5)
          expect(await measure()).toEqual(before)
          await area.evaluate((el) => {
            el.scrollTop = el.scrollHeight
          })
          expect(await measure()).toEqual(before)
          await area.evaluate((el) => {
            el.scrollTop = 0
          })
        }
      }
    }
  }
})

test('extreme viewport never adds a full-height outer scrollbar', async ({ page }) => {
  await setup(page, ['MGR', 'MGR:user:list'])
  await page.goto('/baosys-manager/#/users-management')
  await expect(page.locator('.admin-content')).toBeVisible()
  for (const viewport of [
    { width: 240, height: 120 },
    { width: 320, height: 180 },
    { width: 1024, height: 180 },
    { width: 1440, height: 900 }
  ]) {
    await page.setViewportSize(viewport)
    // Deliver matchMedia change events before manually toggling the sidebar.
    await page.evaluate(
      () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    )
    for (const collapsed of [false, true]) {
      const toggle = page.locator('.sidebar-toggle')
      if ((await toggle.getAttribute('aria-expanded')) !== String(!collapsed)) await toggle.click()
      await expect(page.locator('.admin-sider')).toHaveCSS('width', collapsed ? '64px' : '232px')
      for (const selector of ['html', 'body', '#app', '.admin-layout', '.admin-main']) {
        await expect(page.locator(selector)).toHaveCSS('overflow-y', 'hidden')
      }
      expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(
        true
      )
      await expect(page.locator('.admin-content')).toHaveCSS('overflow-y', 'auto')
      await expect(page.locator('.sidebar-menu .arco-menu-inner')).toHaveCSS('overflow-y', 'auto')
    }
  }
  await page.goto('/baosys-manager/#/login')
  await expect(page.locator('.admin-layout')).toHaveCount(0)
  await expect(page.locator('html')).not.toHaveCSS('overflow-y', 'hidden')
})

test('shared CSS tokens control shell dimensions and keep scroll areas aligned', async ({
  page
}) => {
  await setup(page, ['MGR', 'MGR:user:list'])
  await page.goto('/baosys-manager/#/users-management')
  await expect(page.locator('.admin-header')).toBeVisible()
  await page.evaluate(() => {
    const style = document.documentElement.style
    style.setProperty('--baosys-workspace-max-width', '600px')
    style.setProperty('--baosys-header-height', '88px')
    style.setProperty('--baosys-sidebar-width', '280px')
    style.setProperty('--baosys-sidebar-collapsed-width', '80px')
    style.setProperty('--baosys-workspace-padding-left', '32px')
    style.setProperty('--baosys-workspace-padding-right', '20px')
  })
  await expect(page.locator('.baosys-page')).toHaveCSS('max-width', '600px')
  await expect(page.locator('.admin-header')).toHaveCSS('height', '88px')
  await expect(page.locator('.admin-sider')).toHaveCSS('width', '280px')
  await expect(page.locator('.admin-content')).toHaveCSS('padding-left', '32px')
  await expect(page.locator('.admin-content')).toHaveCSS('padding-right', '20px')
  await expectAlignedScrollAreas(page)
  await page.locator('.sidebar-toggle').click()
  await expect(page.locator('.admin-sider')).toHaveCSS('width', '80px')
  await expectAlignedScrollAreas(page)
})
