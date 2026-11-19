import type { Component } from 'vue'
import type { RouteRecordNormalized } from 'vue-router'
import type { SidebarItem } from './types'
export interface MenuConfig {
  order?: number
  icon?: Component
  group?: { key: string; titleKey: string; icon?: Component; order?: number }
}
declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    titleKey?: string
    requiredPermission?: string
    menu?: MenuConfig
  }
}
/** Only routes explicitly marked with meta.menu appear in navigation. */
export function createRouteMenus(
  routes: RouteRecordNormalized[],
  t: (key: string) => string,
  can: (permission: string) => boolean
): SidebarItem[] {
  const result: { item: SidebarItem; order: number }[] = []
  const groups = new Map<string, SidebarItem>()
  const visible = routes
    .filter(
      (r) =>
        !r.aliasOf && r.meta.menu && (!r.meta.requiredPermission || can(r.meta.requiredPermission))
    )
    .sort((a, b) => (a.meta.menu!.order ?? 0) - (b.meta.menu!.order ?? 0))
  for (const route of visible) {
    const menu = route.meta.menu!
    const item: SidebarItem = {
      key: route.path,
      label: t(route.meta.titleKey ?? ''),
      icon: menu.icon
    }
    if (menu.group) {
      let group = groups.get(menu.group.key)
      if (!group) {
        group = {
          key: menu.group.key,
          label: t(menu.group.titleKey),
          icon: menu.group.icon,
          children: []
        }
        groups.set(menu.group.key, group)
        result.push({ item: group, order: menu.group.order ?? menu.order ?? 0 })
      }
      group.children!.push(item)
    } else result.push({ item, order: menu.order ?? 0 })
  }
  return result.sort((a, b) => a.order - b.order).map((entry) => entry.item)
}
