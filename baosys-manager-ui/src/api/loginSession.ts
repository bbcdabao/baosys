import { MANAGER_API_PREFIX } from '@/constants/api'
import type { LoginSession } from '@/types/loginSession'
import type { PageResponse } from '@/types/page'
import { get } from '@/utils/request'

export const listLoginSessions = (
  userId?: number,
  userName = '',
  page = 0,
  size = 20
): Promise<PageResponse<LoginSession>> =>
  get<PageResponse<LoginSession>>(`${MANAGER_API_PREFIX}/v1/login-sessions`, {
    userId,
    userName,
    page,
    size
  })
