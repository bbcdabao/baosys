import { systemHomeUrl } from './systemHomeUrl'

export interface SystemDefinition {
  systemType: string
  uiBaseUrl: string
  homePath: string
  name: Record<string, string>
  permissions: Array<{ code: string; name: Record<string, string> }>
}

export const describeSystemCatalog = (systems: SystemDefinition[], locale: string) => ({
  systems: systems.map((system) => ({
    value: system.systemType,
    label: system.name[locale] || system.systemType,
    homeUrl: systemHomeUrl(system.uiBaseUrl, system.homePath)
  })),
  permissionLabel: (code: string): string => {
    const permission = systems
      .flatMap((system) => system.permissions)
      .find((item) => item.code === code)
    return permission?.name[locale] || code
  }
})
