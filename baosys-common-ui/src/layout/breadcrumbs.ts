import type { BreadcrumbItem } from './types'

/** Page entries follow matched route order; menu groups are not route ancestors. */
export const createBreadcrumbs = (subsystem: string, pages: BreadcrumbItem[]): BreadcrumbItem[] => [
  { key: 'baosys', label: 'baosys' },
  { key: 'subsystem', label: subsystem },
  ...pages
]
