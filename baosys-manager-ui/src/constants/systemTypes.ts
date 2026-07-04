import type { SystemType } from '@/types/user'

export const SYSTEM_TYPE_OPTIONS: Array<{
  value: SystemType
  labelKey: string
}> = [
  { value: 'MANAGER', labelKey: 'platform.manager' },
  { value: 'SIMULATION', labelKey: 'platform.simulation' },
  { value: 'SIMULATORS', labelKey: 'platform.simulators' }
]

export const systemTypeLabelKey = (value: SystemType): string =>
  SYSTEM_TYPE_OPTIONS.find(option => option.value === value)?.labelKey || value
