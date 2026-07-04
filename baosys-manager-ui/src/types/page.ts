/**
 * 与后端 PageResponse<T> 对应的统一分页响应。
 */
export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}
