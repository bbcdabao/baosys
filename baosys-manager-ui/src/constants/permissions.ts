import type { SystemType } from '@/types/user'

export interface PermissionOption {
  code: string
  labelKey: string
}

export const MANAGER_PERMISSION_OPTIONS: PermissionOption[] = [
  { code: 'user:list', labelKey: 'permission.manager.list' },
  { code: 'user:add', labelKey: 'permission.manager.add' },
  { code: 'user:update', labelKey: 'permission.manager.update' },
  { code: 'user:delete', labelKey: 'permission.manager.delete' },
  { code: 'user:session:list', labelKey: 'permission.manager.sessionList' },
  { code: 'user:session:kick', labelKey: 'permission.manager.sessionKick' }
]

// 预留独立权限目录，后续扩展时不会把其他系统的权限混入管理端。
export const SIMULATION_PERMISSION_OPTIONS: PermissionOption[] = []
export const SIMULATORS_PERMISSION_OPTIONS: PermissionOption[] = []

export const PERMISSION_OPTIONS_BY_SYSTEM_TYPE: Record<
  SystemType,
  PermissionOption[]
> = {
  MANAGER: MANAGER_PERMISSION_OPTIONS,
  SIMULATION: SIMULATION_PERMISSION_OPTIONS,
  SIMULATORS: SIMULATORS_PERMISSION_OPTIONS
}

export const permissionOptionsFor = (
  systemType: SystemType
): PermissionOption[] => PERMISSION_OPTIONS_BY_SYSTEM_TYPE[systemType]

export const permissionLabelKey = (
  systemType: SystemType,
  code: string
): string =>
  permissionOptionsFor(systemType).find(permission => permission.code === code)
    ?.labelKey || code
