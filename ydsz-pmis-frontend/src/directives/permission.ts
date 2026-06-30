import type { App, Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/store/modules/user'

/**
 * v-permission 权限指令
 *
 * 用法:
 *   <el-button v-permission="['system:user:create']">新增</el-button>
 *   <el-button v-permission="'system:user:create'">新增</el-button>
 *   <el-button v-permission.all="['system:user:*']">管理员</el-button>
 */

type PermissionValue = string | string[]

interface PermissionBinding extends Omit<DirectiveBinding<PermissionValue>, 'value'> {
  value?: PermissionValue
  modifiers?: { all?: boolean; or?: boolean }
}

function check(perm: string | string[], all = false): boolean {
  const userStore = useUserStore()
  const permissions = userStore.permissions

  // 超级权限
  if (permissions.includes('*:*:*')) return true

  if (typeof perm === 'string') {
    return permissions.includes(perm)
  }

  if (Array.isArray(perm)) {
    return all ? perm.every((p) => permissions.includes(p)) : perm.some((p) => permissions.includes(p))
  }

  return false
}

const permissionDirective: Directive<HTMLElement, PermissionValue> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
    const { value, modifiers } = binding
    if (!value) return
    if (!check(value, modifiers?.all as boolean | undefined)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export function setupPermissionDirective(app: App): void {
  app.directive('permission', permissionDirective)
}

export default permissionDirective
