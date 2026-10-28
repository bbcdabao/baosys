/** 从扁平权限中派生系统入口，仅用于展示和表单分组；接口权限不隐式授予入口。 */
export const systemTypesFromPermissions = (permissions: readonly string[]): string[] =>
  [...new Set(permissions.filter((permission) => /^[A-Z]+$/.test(permission)))].sort()
