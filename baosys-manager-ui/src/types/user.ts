/** 与后端 SystemType 枚举一致的业务系统标识。 */
export type SystemType = 'MANAGER' | 'SIMULATION' | 'SIMULATORS'

export interface UserSummary {
  id: number
  name: string
  systemType: SystemType
  permissions: string[]
  status: boolean
}

export interface UserCreateRequest {
  name: string
  pass: string
  systemType: SystemType
  permissions: string[]
}

export interface UserUpdateRequest extends UserCreateRequest {
  id: number
}
