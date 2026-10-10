/** 由根目录 baosys-systems.yml 声明、后端校验的业务系统标识。 */
export type SystemType = string

export interface UserSummary {
  id: number
  name: string
  systemType: SystemType
  permissions: string[]
  enabled: boolean
  status: boolean
}

export interface UserCreateRequest {
  name: string
  pass: string
  systemType: SystemType
  permissions: string[]
  enabled: boolean
}

export interface UserUpdateRequest extends UserCreateRequest {
  id: number
}
