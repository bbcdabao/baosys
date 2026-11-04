/** 同源标签页认证操作协调。版本标记不代表身份，不存储任何凭据。 */
export type AuthVersion = {
  accountEpoch: string
  refreshRevision: string
  lastOperation: 'initial' | 'login' | 'refresh' | 'logout' | 'invalid'
}

export class AccountChangedError extends Error {
  constructor() { super('登录账户已变更，请重新操作') }
}
export class LoginExpiredError extends Error {
  constructor() { super('登录已失效，请重新登录') }
}

const key = 'baosys.auth.version'
const lockName = 'baosys.auth.operation'
let memory: AuthVersion = { accountEpoch: 'initial', refreshRevision: 'initial', lastOperation: 'initial' }
let storageAvailable = true
const listeners = new Set<(version: AuthVersion) => void>()
const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(lockName)

export function authVersion(): AuthVersion {
  if (!storageAvailable) return { ...memory }
  try {
    const value = localStorage.getItem(key)
    if (value) {
      const parsed = JSON.parse(value) as AuthVersion
      if (typeof parsed.accountEpoch === 'string' && typeof parsed.refreshRevision === 'string'
        && ['initial', 'login', 'refresh', 'logout', 'invalid'].includes(parsed.lastOperation)) memory = parsed
    }
  } catch { storageAvailable = false }
  return { ...memory }
}

let observedEpoch = authVersion().accountEpoch
function receive() {
  const value = authVersion()
  if (observedEpoch !== value.accountEpoch) {
    observedEpoch = value.accountEpoch
    listeners.forEach(listener => listener(value))
  }
}
channel?.addEventListener('message', receive)
window.addEventListener('storage', event => { if (event.key === key || event.key === null) receive() })
window.addEventListener('focus', receive)

export function onAccountChange(listener: (version: AuthVersion) => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

function publish(operation: Exclude<AuthVersion['lastOperation'], 'initial'>) {
  const previous = authVersion()
  const unique = () => typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`
  memory = {
    accountEpoch: operation === 'refresh' ? previous.accountEpoch : unique(),
    refreshRevision: unique(), lastOperation: operation
  }
  observedEpoch = memory.accountEpoch
  try { localStorage.setItem(key, JSON.stringify(memory)) } catch { storageAvailable = false }
  channel?.postMessage('changed')
}

// 页内队列也覆盖无 Web Locks 的降级环境，认证操作失败不阻塞后续操作。
let queue: Promise<unknown> = Promise.resolve()
function exclusive<T>(task: () => Promise<T>): Promise<T> {
  const work = queue.then(async () => {
    authVersion()
    if (!storageAvailable || !navigator.locks) return task()
    const abort = new AbortController()
    const timer = setTimeout(() => abort.abort(), 15_000)
    try {
      return await navigator.locks.request(lockName, { signal: abort.signal }, async () => {
        clearTimeout(timer)
        return task()
      })
    } catch (error) {
      if (abort.signal.aborted) throw new Error('等待认证操作超时，请重试')
      throw error
    } finally { clearTimeout(timer) }
  })
  queue = work.catch(() => undefined)
  return work
}

export function changeLogin<T>(operation: 'login' | 'logout', task: () => Promise<T>): Promise<T> {
  return exclusive(async () => {
    const result = await task()
    publish(operation)
    return result
  })
}

const pending = new Map<string, Promise<void>>()
export function refreshLogin(
  sent: AuthVersion, refresh: () => Promise<unknown>, isUnauthorized: (error: unknown) => boolean
): Promise<void> {
  const requestKey = `${sent.accountEpoch}:${sent.refreshRevision}`
  const existing = pending.get(requestKey)
  if (existing) return existing
  const work = exclusive(async () => {
    const current = authVersion()
    if (current.accountEpoch !== sent.accountEpoch) throw new AccountChangedError()
    if (current.lastOperation === 'logout' || current.lastOperation === 'invalid') throw new LoginExpiredError()
    if (current.refreshRevision !== sent.refreshRevision) return
    try { await refresh() } catch (error) {
      if (isUnauthorized(error)) {
        publish('invalid')
        throw new LoginExpiredError()
      }
      throw error
    }
    publish('refresh')
  }).finally(() => { pending.delete(requestKey) })
  pending.set(requestKey, work)
  return work
}
