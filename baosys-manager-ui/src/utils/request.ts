import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

import emitter from '@/event-bus'
import { authVersion, changeLogin, refreshLogin, AccountChangedError, LoginExpiredError, type AuthVersion } from './authCoordinator'
import type { ApiResult } from '@/types/api'

const client = axios.create({
  baseURL: '/',
  timeout: 10_000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

// 通过端点获取 Spring 的掩码 CSRF 令牌，并由服务端设置 HttpOnly XSRF-TOKEN Cookie。
// 并发请求共用一次获取操作；令牌不落盘，JavaScript 不读取 Cookie。
let csrfPromise: Promise<{ headerName: string; token: string }> | null = null
const prepareCsrf = async (config: InternalAxiosRequestConfig) => {
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(config.method?.toUpperCase() || 'GET')) {
    csrfPromise ??= axios
      .get<{ headerName: string; token: string }>('/csrf', {
        withCredentials: true,
        timeout: 10_000
      })
      .then((response) => response.data)
      .finally(() => {
        csrfPromise = null
      })
    const csrf = await csrfPromise
    config.headers.set(csrf.headerName, csrf.token)
  }
  return config
}
const authClient = axios.create({ baseURL: '/', timeout: 10_000, withCredentials: true,
  headers: { 'Content-Type': 'application/json' } })
authClient.interceptors.request.use(prepareCsrf)
type VersionedConfig = InternalAxiosRequestConfig & { authVersion?: AuthVersion; retried?: boolean }
client.interceptors.request.use(async (config: VersionedConfig) => {
  config.authVersion ??= authVersion()
  await prepareCsrf(config)
  if (config.authVersion && config.authVersion.accountEpoch !== authVersion().accountEpoch) {
    throw new AccountChangedError()
  }
  return config
})

const authPrefix = '/api/baosys-manager/v1/browser-auth'
client.interceptors.response.use(
  (response) => {
    const sent = (response.config as VersionedConfig).authVersion
    if (sent && sent.accountEpoch !== authVersion().accountEpoch) throw new AccountChangedError()
    return response
  },
  async (error: AxiosError) => {
    const config = error.config as VersionedConfig | undefined
    if (error.response?.status !== 401 || config?.url?.startsWith(authPrefix)) throw error
    if (!config || config.retried || !config.authVersion) throw error
    config.retried = true
    const sent = config.authVersion
    try {
      await refreshLogin(sent, () => perform<void>({ method: 'POST', url: authPrefix + '/refresh', data: {} }, true),
        failure => failure instanceof AxiosError && failure.response?.status === 401)
      if (authVersion().accountEpoch !== sent.accountEpoch) throw new AccountChangedError()
      config.authVersion = authVersion()
      return client.request(config)
    } catch (failure) {
      if (failure instanceof LoginExpiredError) emitter.emit('go-login')
      throw failure
    }
  }
)

export const logout = (): Promise<void> => changeLogin('logout',
  () => perform<void>({ method: 'POST', url: authPrefix + '/logout', data: {} }, true)).catch(reportFailure)

export const login = (username: string, password: string, redirectUrl?: string) => changeLogin('login',
  () => perform<{ redirectUrl: string }>({ method: 'POST', url: authPrefix + '/login',
    data: { username, password, redirectUrl } }, true)).catch(reportFailure)

function reportFailure(error: unknown): never {
  if (!(error instanceof AxiosError)) throw error
  throw new Error(error.response?.data?.message || (error.response?.status === 401
    ? '账户凭证不正确或登录已失效，请重新登录'
    : error.response?.status === 403 ? '请求被拒绝，请检查权限或刷新页面后重试'
      : error.code === 'ECONNABORTED' ? '请求超时，请重试' : '服务暂时不可用，请稍后重试'))
}

const perform = async <T>(config: AxiosRequestConfig, authentication = false): Promise<T> => {
  try {
    const response = await (authentication ? authClient : client).request<ApiResult<T>>(config)
    if (response.data.code !== 'SUCCESS') {
      throw new Error(response.data.message || 'Request failed')
    }
    return response.data.data
  } catch (error) {
    if (authentication) throw error
    if (!(error instanceof AxiosError)) {
      throw error
    }
    const message =
      error.response?.data?.message ||
      (error.response?.status === 401
        ? '账户凭证不正确或登录已失效，请重新登录'
        : error.response?.status === 403
          ? '请求被拒绝，请检查权限或刷新页面后重试'
          : error.code === 'ECONNABORTED'
            ? 'Request timed out'
            : 'Service is temporarily unavailable')
    throw new Error(message)
  }
}

export const get = <TResponse>(url: string, params?: Record<string, unknown>): Promise<TResponse> =>
  perform<TResponse>({ method: 'GET', url, params })

export const post = <TResponse, TRequest>(
  url: string,
  data: TRequest,
  config?: AxiosRequestConfig
): Promise<TResponse> => perform<TResponse>({ ...config, method: 'POST', url, data })

export const put = <TResponse, TRequest>(url: string, data: TRequest): Promise<TResponse> =>
  perform<TResponse>({ method: 'PUT', url, data })

export const remove = <TResponse>(url: string): Promise<TResponse> =>
  perform<TResponse>({ method: 'DELETE', url })
