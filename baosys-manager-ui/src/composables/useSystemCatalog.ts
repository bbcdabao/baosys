import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { MANAGER_API_PREFIX } from '@/constants/api'
import { get } from '@/utils/request'

interface LocalizedName {
  [locale: string]: string
}
interface SystemDefinition {
  systemType: string
  name: LocalizedName
  permissions: Array<{ code: string; name: LocalizedName }>
}

const systems = ref<SystemDefinition[]>([])

// 登录恢复时加载，失败向上抛出，避免以空目录继续编辑账号。
export const loadSystemCatalog = async (): Promise<void> => {
  systems.value = await get<SystemDefinition[]>(`${MANAGER_API_PREFIX}/v1/systems`)
}

export const useSystemCatalog = () => {
  const { locale } = useI18n()
  const label = (names: LocalizedName | undefined, fallback: string): string =>
    names?.[locale.value] || fallback
  const systemOptions = computed(() =>
    systems.value.map((system) => ({
      value: system.systemType,
      label: label(system.name, system.systemType)
    }))
  )
  const systemLabel = (type: string): string =>
    label(systems.value.find((system) => system.systemType === type)?.name, type)
  const permissionOptionsFor = (type: string) =>
    (systems.value.find((system) => system.systemType === type)?.permissions || []).map(
      (permission) => ({ code: permission.code, label: label(permission.name, permission.code) })
    )
  const permissionLabel = (code: string): string => {
    if (/^[A-Z]+$/.test(code)) {
      return systemLabel(code)
    }
    const permission = systems.value
      .flatMap((system) => system.permissions)
      .find((permission) => permission.code === code)
    return permission ? label(permission.name, code) : code
  }
  return { systemOptions, systemLabel, permissionOptionsFor, permissionLabel }
}
