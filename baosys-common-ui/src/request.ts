import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { authVersion, AccountChangedError, type AuthVersion } from './authCoordinator'
import type { ApiResponse } from './api'

export class RequestError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message)
    this.name = 'RequestError'
  }
}

export function reportFailure(error: unknown): never {
  if (!(error instanceof AxiosError)) throw error
  throw new RequestError(error.response?.data?.message || (
    error.response?.status === 401 ? '账户凭证不正确或登录已失效，请重新登录' :
    error.response?.status === 403 ? '请求被拒绝，请检查权限或刷新页面后重试' :
    error.code === 'ECONNABORTED' ? '请求超时，请重试' : '服务暂时不可用，请稍后重试'
  ), error.response?.status)
}

type CsrfResponse = { enabled: false } | { enabled?: true; headerName: string; token: string }
type VersionedConfig = InternalAxiosRequestConfig & { authVersion?: AuthVersion; retried?: boolean }
export type RequestOptions = {
  csrfPath: string
  authPrefix?: string
  recoverSession: (sent: AuthVersion) => Promise<void>
}

/** Each application owns its CSRF endpoint and recovery policy; retry and account checks are shared. */
export function createRequests(options: RequestOptions) {
  const defaults = { baseURL: '/', timeout: 10_000, withCredentials: true,
    headers: { 'Content-Type': 'application/json' } }
  const client = axios.create(defaults)
  const authClient = axios.create(defaults)
  let csrfPromise: Promise<CsrfResponse> | null = null
  async function prepareCsrf(config: InternalAxiosRequestConfig) {
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(config.method?.toUpperCase() || 'GET')) {
      csrfPromise ??= axios.get<CsrfResponse>(options.csrfPath, { withCredentials: true, timeout: 10_000 })
        .then(response => response.data).finally(() => { csrfPromise = null })
      const csrf = await csrfPromise
      if (csrf.enabled !== false) config.headers.set(csrf.headerName, csrf.token)
    }
    return config
  }
  authClient.interceptors.request.use(prepareCsrf)
  client.interceptors.request.use(async (config: VersionedConfig) => {
    config.authVersion ??= authVersion()
    await prepareCsrf(config)
    if (config.authVersion.accountEpoch !== authVersion().accountEpoch) throw new AccountChangedError()
    return config
  })
  client.interceptors.response.use(response => {
    const sent = (response.config as VersionedConfig).authVersion
    if (sent && sent.accountEpoch !== authVersion().accountEpoch) throw new AccountChangedError()
    return response
  }, async (error: AxiosError) => {
    const config = error.config as VersionedConfig | undefined
    if (error.response?.status !== 401 || (options.authPrefix && config?.url?.startsWith(options.authPrefix))) throw error
    if (!config || config.retried || !config.authVersion) throw error
    config.retried = true
    const sent = config.authVersion
    await options.recoverSession(sent)
    if (authVersion().accountEpoch !== sent.accountEpoch) throw new AccountChangedError()
    config.authVersion = authVersion()
    return client.request(config)
  })
  async function perform<T>(config: AxiosRequestConfig, authentication = false): Promise<T> {
    try {
      const response = await (authentication ? authClient : client).request<ApiResponse<T>>(config)
      if (response.data.code !== 'SUCCESS') throw new RequestError(response.data.message || '请求失败')
      return response.data.data
    } catch (error) {
      if (authentication) throw error
      return reportFailure(error)
    }
  }
  return {
    perform,
    get: <T>(url: string, params?: Record<string, unknown>) => perform<T>({ method: 'GET', url, params }),
    post: <T, D>(url: string, data: D, config?: AxiosRequestConfig) => perform<T>({ ...config, method: 'POST', url, data }),
    put: <T, D>(url: string, data: D) => perform<T>({ method: 'PUT', url, data }),
    remove: <T>(url: string) => perform<T>({ method: 'DELETE', url })
  }
}
