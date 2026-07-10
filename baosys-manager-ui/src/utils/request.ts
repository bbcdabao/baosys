import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from 'axios'

import { MANAGER_API_PREFIX } from '@/constants/api'
import emitter from '@/event-bus'
import type { ApiResult } from '@/types/api'
import { CSRF_TOKEN_STORAGE_KEY, saveCsrfToken } from '@/utils/authSession'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRefresh?: boolean
  }
  interface InternalAxiosRequestConfig {
    _retry?: boolean
  }
}

const CSRF_TOKEN_HEADER_NAME = 'X-CSRF-Token'
const client = axios.create({
  baseURL: '/',
  timeout: 10_000,
  withCredentials: true,
  skipAuthRefresh: false,
  headers: { 'Content-Type': 'application/json' }
})

// 刷新使用独立实例，避免刷新接口的 401 再次进入业务响应拦截器。
const refreshClient = axios.create({
  baseURL: '/',
  timeout: 10_000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})
let refreshPromise: Promise<void> | null = null

client.interceptors.request.use(config => {
  const method = config.method?.toUpperCase()
  if (method && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfToken = sessionStorage.getItem(CSRF_TOKEN_STORAGE_KEY)
    if (csrfToken) config.headers.set(CSRF_TOKEN_HEADER_NAME, csrfToken)
  }
  return config
})

client.interceptors.response.use(
  response => response,
  async (error: AxiosError<ApiResult<unknown>>) => {
    const original = error.config

    if (error.response?.status !== 401 || !original || original.skipAuthRefresh) {
      return Promise.reject(error)
    }

    if (original._retry) {
      emitter.emit('go-login')
      return Promise.reject(error)
    }

    original._retry = true

    try {
      await refreshAccessToken()
      return await client(original)
    } catch (refreshOrRetryError) {
      emitter.emit('go-login')
      return Promise.reject(refreshOrRetryError)
    }
  }
)

const captureCsrfToken = (value: unknown): void => {
  if (typeof value === 'string' && value) saveCsrfToken(value)
}

const refreshAccessToken = async (): Promise<void> => {
  if (refreshPromise) return refreshPromise

  const csrfToken = sessionStorage.getItem(CSRF_TOKEN_STORAGE_KEY)
  refreshPromise = refreshClient
    .post<ApiResult<void>>(
      `${MANAGER_API_PREFIX}/v1/auth/refresh`,
      undefined,
      {
        headers: csrfToken
          ? { [CSRF_TOKEN_HEADER_NAME]: csrfToken }
          : undefined
      }
    )
    .then(response => {
      if (response.data.code !== 'SUCCESS') {
        throw new Error(response.data.message || 'Session renewal failed')
      }
      captureCsrfToken(response.headers[CSRF_TOKEN_HEADER_NAME.toLowerCase()])
    })
    .finally(() => {
      refreshPromise = null
    })
  return refreshPromise
}

const perform = async <T>(config: AxiosRequestConfig): Promise<T> => {
  try {
    const response = await client.request<ApiResult<T>>(config)
    if (response.data.code !== 'SUCCESS') {
      throw new Error(response.data.message || 'Request failed')
    }
    return response.data.data
  } catch (error) {
    if (!(error instanceof AxiosError)) throw error
    const message = error.response?.data?.message
      || (error.code === 'ECONNABORTED'
        ? 'Request timed out'
        : 'Service is temporarily unavailable')
    throw new Error(message)
  }
}

export const get = <TResponse>(
  url: string,
  params?: Record<string, unknown>
): Promise<TResponse> =>
  perform<TResponse>({ method: 'GET', url, params })

export const post = <TResponse, TRequest>(
  url: string,
  data: TRequest,
  config?: AxiosRequestConfig
): Promise<TResponse> =>
  perform<TResponse>({ ...config, method: 'POST', url, data })

export const put = <TResponse, TRequest>(
  url: string,
  data: TRequest
): Promise<TResponse> =>
  perform<TResponse>({ method: 'PUT', url, data })

export const remove = <TResponse>(url: string): Promise<TResponse> =>
  perform<TResponse>({ method: 'DELETE', url })
