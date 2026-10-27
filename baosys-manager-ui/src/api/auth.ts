export { logout } from '@/utils/request'
import { post } from '@/utils/request'

export const login = (username: string, password: string, redirectUrl?: string) =>
  post<{ redirectUrl: string }, { username: string; password: string; redirectUrl?: string }>(
    '/api/baosys-manager/v1/browser-auth/login',
    { username, password, redirectUrl }
  )
