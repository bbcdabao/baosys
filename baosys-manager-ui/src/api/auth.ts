import { MANAGER_API_PREFIX } from '@/constants/api'
import type { LoginRequest } from '@/types/auth'
import { post } from '@/utils/request'

export const login = (payload: LoginRequest): Promise<void> =>
  post<void, LoginRequest>(
    `${MANAGER_API_PREFIX}/v1/auth/login`,
    payload,
    { skipAuthRefresh: true }
  )

export const logout = (): Promise<void> =>
  post<void, undefined>(
    `${MANAGER_API_PREFIX}/v1/auth/logout`,
    undefined
  )
