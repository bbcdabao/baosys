import type { Component } from 'vue'

export interface SidebarItem {
  key: string
  label: string
  icon?: Component
  children?: SidebarItem[]
}

export interface BreadcrumbItem {
  key: string
  label: string
}

export interface HeaderLabels {
  theme: string
  fullscreen: string
  exitFullscreen: string
  logout: string
}

export interface LocaleOption {
  value: string
  label: string
}
