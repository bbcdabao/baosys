import { test, expect } from '@playwright/test'

const prefix = '/api/baosys-manager/v1/browser-auth'
const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

async function setup(context, { barrier = false, refreshStatus = 200 } = {}) {
  const state = { refreshes: 0, reads: 0, writes: 0, generation: 0, operations: [] }
  const waiting = deferred()
  await context.addCookies([{ name: 'BAOSYS_REFRESH_TOKEN', value: 'r0',
    domain: 'localhost', path: prefix, httpOnly: true, sameSite: 'Lax' }])
  await context.route('**/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF', token: 'test' } }))
  await context.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith("/csrf")) return route.fallback()
    const cookie = request.headers().cookie || ''
    if (path === prefix + '/refresh') {
      state.refreshes++
      state.operations.push('refresh')
      expect(request.headers()['x-csrf']).toBe('test')
      if (refreshStatus !== 200) return route.fulfill({ status: refreshStatus, json: { message: 'failed' } })
      if (!cookie.includes(`BAOSYS_REFRESH_TOKEN=r${state.generation}`)) {
        return route.fulfill({ status: 401, json: { message: 'rotated' } })
      }
      state.generation++
      // A real HttpOnly Cookie is installed by the browser from the HTTP response.
      await context.addCookies([{ name: 'BAOSYS_REFRESH_TOKEN', value: `r${state.generation}`,
        domain: 'localhost', path: prefix, httpOnly: true, sameSite: 'Lax' }])
      return route.fulfill({ headers: { 'set-cookie': `BAOSYS_ACCESS_TOKEN=a${state.generation}; Path=/; HttpOnly; SameSite=Lax` },
        json: { code: 'SUCCESS', data: null } })
    }
    if (path === prefix + '/login' || path === prefix + '/logout') {
      state.operations.push(path.endsWith('login') ? 'login' : 'logout')
      return route.fulfill({ json: { code: 'SUCCESS', data: { redirectUrl: '/' } } })
    }
    state.reads++
    if (request.method() !== 'GET') state.writes++
    if (cookie.includes('BAOSYS_ACCESS_TOKEN=')) {
      return route.fulfill({ json: { code: 'SUCCESS', data: 'ok' } })
    }
    if (barrier) {
      if (state.reads >= 2) waiting.resolve()
      await waiting.promise
    }
    return route.fulfill({ status: 401, json: { message: 'expired' } })
  })
  return state
}

async function pageFor(context) {
  const page = await context.newPage()
  await page.goto('/baosys-manager/tests/auth-harness.html')
  await page.evaluate(async () => {
    window.api = await import('/baosys-manager/src/utils/request.ts')
    window.coordination = await import('/baosys-manager/src/utils/authCoordinator.ts')
    window.loginEvents = 0
    const { default: emitter } = await import('/baosys-manager/src/event-bus.ts')
    emitter.on('go-login', () => window.loginEvents++)
  })
  return page
}

test('two tabs rotate one shared cookie once and both retry successfully', async ({ context }) => {
  const state = await setup(context, { barrier: true })
  const a = await pageFor(context)
  const b = await pageFor(context)
  const results = await Promise.all([a, b].map(page => page.evaluate(() => window.api.get('/api/test'))))
  expect(results).toEqual(['ok', 'ok'])
  expect(state.refreshes).toBe(1)
  expect(state.reads).toBe(4)
  const stored = await a.evaluate(() => JSON.parse(localStorage.getItem('baosys.auth.version')))
  expect(Object.keys(stored).sort()).toEqual(['accountEpoch', 'lastOperation', 'refreshRevision'])
  expect((await context.cookies()).find(cookie => cookie.name === 'BAOSYS_ACCESS_TOKEN').httpOnly).toBe(true)
})

test('late 401 reuses a refresh completed in another tab', async ({ context }) => {
  const state = await setup(context)
  const a = await pageFor(context)
  const b = await pageFor(context)
  const arrived = deferred()
  const release = deferred()
  await b.route('**/api/late', async route => {
    if (route.request().headers().cookie?.includes('BAOSYS_ACCESS_TOKEN=')) {
      return route.fulfill({ json: { code: 'SUCCESS', data: 'ok' } })
    }
    arrived.resolve()
    await release.promise
    await route.fulfill({ status: 401, json: {} })
  })
  const late = b.evaluate(() => window.api.get('/api/late'))
  await arrived.promise
  expect(await a.evaluate(() => window.api.get('/api/test'))).toBe('ok')
  release.resolve()
  expect(await late).toBe('ok')
  expect(state.refreshes).toBe(1)
})

test('old account write is not replayed after another tab logs in', async ({ context }) => {
  const state = await setup(context)
  const a = await pageFor(context)
  const b = await pageFor(context)
  const arrived = deferred()
  const release = deferred()
  let writes = 0
  await a.route('**/api/write', async route => {
    writes++
    arrived.resolve()
    await release.promise
    await route.fulfill({ status: 401, json: {} })
  })
  const old = a.evaluate(() => window.api.post('/api/write', {}).catch(error => error.message))
  await arrived.promise
  await b.evaluate(() => window.api.login('new-account', 'password'))
  release.resolve()
  expect(await old).toContain('账户已变更')
  expect(writes).toBe(1)
  expect(state.refreshes).toBe(0)
})

test('logout waits for refresh and notifies other tabs of the new epoch', async ({ context }) => {
  await setup(context)
  const a = await pageFor(context)
  const b = await pageFor(context)
  const entered = deferred()
  const release = deferred()
  await a.route('**/browser-auth/refresh', async route => {
    entered.resolve()
    await release.promise
    await route.fulfill({ json: { code: 'SUCCESS', data: null } })
  })
  await a.evaluate(() => { window.changes = []; window.coordination.onAccountChange(v => window.changes.push(v.lastOperation)) })
  const refresh = a.evaluate(() => window.coordination.refreshLogin(window.coordination.authVersion(),
    () => fetch('/api/baosys-manager/v1/browser-auth/refresh', { method: 'POST' }), () => false))
  await entered.promise
  const logout = b.evaluate(() => window.api.logout())
  await expect.poll(() => b.evaluate(async () => (await navigator.locks.query()).pending.length)).toBe(1)
  release.resolve()
  await Promise.all([refresh, logout])
  await expect.poll(() => a.evaluate(() => window.changes)).toContain('logout')
})

test('refresh server error retains epoch and does not emit logout', async ({ context }) => {
  const state = await setup(context, { refreshStatus: 503 })
  const page = await pageFor(context)
  const before = await page.evaluate(() => window.coordination.authVersion())
  await page.evaluate(() => window.api.get('/api/test').catch(() => null))
  expect(await page.evaluate(() => window.coordination.authVersion())).toEqual(before)
  expect(await page.evaluate(() => window.loginEvents)).toBe(0)
  expect(state.refreshes).toBe(1)
})

test('invalid refresh marks login expired and emits login event once', async ({ context }) => {
  const state = await setup(context, { refreshStatus: 401 })
  const page = await pageFor(context)
  await page.evaluate(() => window.api.get('/api/test').catch(() => null))
  expect(await page.evaluate(() => window.coordination.authVersion().lastOperation)).toBe('invalid')
  expect(await page.evaluate(() => window.loginEvents)).toBe(1)
  expect(state.refreshes).toBe(1)
})

test('without Web Locks concurrent requests within one page still share refresh', async ({ context }) => {
  await context.addInitScript(() => Object.defineProperty(navigator, 'locks', { value: undefined }))
  const state = await setup(context, { barrier: true })
  const page = await pageFor(context)
  expect(await page.evaluate(() => Promise.all([window.api.get('/api/test'), window.api.get('/api/test')]))).toEqual(['ok', 'ok'])
  expect(state.refreshes).toBe(1)
})

test('closing a page releases its authentication lock', async ({ context }) => {
  await setup(context)
  const a = await pageFor(context)
  const b = await pageFor(context)
  await a.evaluate(() => {
    navigator.locks.request('baosys.auth.operation', () => { window.lockHeld = true; return new Promise(() => {}) })
  })
  await expect.poll(() => a.evaluate(() => window.lockHeld)).toBe(true)
  const login = b.evaluate(() => window.api.login('account', 'password'))
  await expect.poll(() => b.evaluate(async () => (await navigator.locks.query()).pending.length)).toBe(1)
  await a.close()
  expect(await login).toEqual({ redirectUrl: '/' })
})

test('lock wait times out without sending an uncoordinated login', async ({ context }) => {
  const state = await setup(context)
  const a = await pageFor(context)
  const b = await pageFor(context)
  await a.evaluate(() => {
    navigator.locks.request('baosys.auth.operation', () => { window.lockHeld = true; return new Promise(() => {}) })
  })
  await expect.poll(() => a.evaluate(() => window.lockHeld)).toBe(true)
  expect(await b.evaluate(() => window.api.login('account', 'password').catch(error => error.message)))
    .toContain('等待认证操作超时')
  expect(state.operations).toEqual([])
})

test('refresh network failure does not broadcast logout or retry indefinitely', async ({ context }) => {
  await setup(context)
  const page = await pageFor(context)
  const before = await page.evaluate(() => window.coordination.authVersion())
  let calls = 0
  await page.route('**/browser-auth/refresh', route => { calls++; return route.abort('failed') })
  await page.evaluate(() => window.api.get('/api/test').catch(() => null))
  expect(await page.evaluate(() => window.coordination.authVersion())).toEqual(before)
  expect(await page.evaluate(() => window.loginEvents)).toBe(0)
  expect(calls).toBe(1)
})

test('unavailable storage falls back to page-local coordination', async ({ context }) => {
  await context.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('Blocked', 'SecurityError') } })
  })
  const state = await setup(context, { barrier: true })
  const page = await pageFor(context)
  expect(await page.evaluate(() => Promise.all([window.api.get('/api/test'), window.api.get('/api/test')]))).toEqual(['ok', 'ok'])
  expect(state.refreshes).toBe(1)
})
