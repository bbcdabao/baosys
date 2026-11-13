import { AxiosError } from 'axios'
import emitter from '@/event-bus'
import { createRequests, reportFailure } from '@baosys/common-ui/request'
import { changeLogin, refreshLogin, LoginExpiredError } from './authCoordinator'
export { RequestError } from '@baosys/common-ui/request'

const authPrefix = '/api/baosys-manager/v1/browser-auth'
const requests = createRequests({
  csrfPath: '/api/baosys-manager/csrf',
  authPrefix,
  async recoverSession(sent) {
    try {
      await refreshLogin(sent,
        () => requests.perform<void>({ method: 'POST', url: authPrefix + '/refresh', data: {} }, true),
        failure => failure instanceof AxiosError && failure.response?.status === 401)
    } catch (failure) {
      if (failure instanceof LoginExpiredError) emitter.emit('go-login')
      throw failure
    }
  }
})
export const { get, post, put, remove } = requests
export const logout = (): Promise<void> => changeLogin('logout', () =>
  requests.perform<void>({ method: 'POST', url: authPrefix + '/logout', data: {} }, true)
).catch(reportFailure)
export const login = (username: string, password: string, redirectUrl?: string) => changeLogin('login', () =>
  requests.perform<{ redirectUrl: string }>({ method: 'POST', url: authPrefix + '/login',
    data: { username, password, redirectUrl } }, true)
).catch(reportFailure)