import type { SystemType } from '@/types/user'

export interface LoginRequest {
  username: string
  password: string
  systemType: SystemType
  loginClient: string
}

export interface CurrentUser {
  userId: number
  username: string
  displayName: string
  /** 当前账号所属的业务系统，用于前端按子系统加载对应能力。 */
  systemType: SystemType
  permissions: string[]
}
