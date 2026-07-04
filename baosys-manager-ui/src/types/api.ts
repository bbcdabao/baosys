export interface ApiResult<T> {
  /**
   * 业务状态码，与 HTTP 状态码相互独立。
   * 仅 `SUCCESS` 表示业务处理成功，其他值均为具体业务错误码，
   * 例如 `AUTH_401_001`、`USER_404_001`。
   */
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
