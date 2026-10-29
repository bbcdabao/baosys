export interface CurrentUser {
  userId: number
  username: string
  displayName: string
  /**
   * 系统入口与接口权限的完整集合。
   */
  permissions: string[]
}
