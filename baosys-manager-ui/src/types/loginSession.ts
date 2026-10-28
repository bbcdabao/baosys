export interface LoginSession {
  id: number
  userId: number
  userName: string
  permissions: string[]
  loginClient: string
  loginAt: string
  expiresAt: string
}
