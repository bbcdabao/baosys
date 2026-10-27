import type { SystemType } from '@/types/user'

export interface CurrentUser {
  userId: number
  username: string
  displayName: string
  /** 当前账号已获授权的业务系统，用于前端按子系统加载对应能力。 */
  systemTypes: SystemType[]
  permissions: string[]
}
