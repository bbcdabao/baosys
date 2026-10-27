import { MANAGER_API_PREFIX } from '@/constants/api'
import type { CurrentUser } from '@/types/auth'
import type { PageResponse } from '@/types/page'
import type { SystemType, UserCreateRequest, UserSummary, UserUpdateRequest } from '@/types/user'
import { get, post, put, remove } from '@/utils/request'

export const getCurrentUser = (): Promise<CurrentUser> =>
  get<CurrentUser>(`${MANAGER_API_PREFIX}/v1/users/me`)

export const listUsers = (
  name = '',
  systemType?: SystemType,
  page = 0,
  size = 20
): Promise<PageResponse<UserSummary>> =>
  get<PageResponse<UserSummary>>(`${MANAGER_API_PREFIX}/v1/users`, {
    name,
    systemType,
    page,
    size
  })

export const createUser = (payload: UserCreateRequest): Promise<UserSummary> =>
  post<UserSummary, UserCreateRequest>(`${MANAGER_API_PREFIX}/v1/users`, payload)

export const updateUser = (payload: UserUpdateRequest): Promise<UserSummary> =>
  put<UserSummary, UserUpdateRequest>(`${MANAGER_API_PREFIX}/v1/users`, payload)

export const deleteUser = (id: number): Promise<void> =>
  remove<void>(`${MANAGER_API_PREFIX}/v1/users/${id}`)

export const kickLoginClient = (userId: number, loginId: number): Promise<void> =>
  remove<void>(`${MANAGER_API_PREFIX}/v1/users/${userId}/login-clients/${loginId}`)

export const kickAllLoginClients = (userId: number): Promise<void> =>
  remove<void>(`${MANAGER_API_PREFIX}/v1/users/${userId}/login-clients`)
