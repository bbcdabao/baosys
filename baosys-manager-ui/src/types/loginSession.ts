import type { SystemType } from '@/types/user'

export interface LoginSession {
  id: number
  userId: number
  userName: string
  systemTypes: SystemType[]
  loginClient: string
  loginAt: string
  expiresAt: string
}
