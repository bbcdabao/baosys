import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

import emitter from '@/event-bus'
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
client.interceptors.request.use(prepareCsrf)

const authPrefix = '/api/baosys-manager/v1/browser-auth'
let refreshPromise: Promise<void> | null = null
client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { retried?: boolean }) | undefined
    if (error.response?.status !== 401 || config?.url?.startsWith(authPrefix)) {
      return Promise.reject(error)
    }
    if (config && !config.retried) {
      config.retried = true
      // 仅 Manager 调用刷新接口；其他子系统收到 401 后跳转至共享登录入口。
      refreshPromise ??= client
        .post(`${authPrefix}/refresh`, {})
        .then(() => undefined)
        .finally(() => {
          refreshPromise = null
        })
      try {
        await refreshPromise
        return client.request(config)
      } catch {
        /* 刷新失败后继续触发下方的登录跳转。 */
      }
    }
    emitter.emit('go-login')
    return Promise.reject(error)
  }
)

export const logout = async (): Promise<void> => {
  await client.post(`${authPrefix}/logout`, {})
}

const perform = async <T>(config: AxiosRequestConfig): Promise<T> => {
  try {
    const response = await client.request<ApiResult<T>>(config)
    if (response.data.code !== 'SUCCESS') {
      throw new Error(response.data.message || 'Request failed')
    }
    return response.data.data
  } catch (error) {
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
