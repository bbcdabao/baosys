import type { SystemType } from '@/types/user'

export interface LoginSession {
  id: number
  userId: number
  userName: string
  systemType: SystemType
  loginClient: string
  loginAt: string
  expiresAt: string
}
