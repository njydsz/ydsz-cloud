/**
 * @file User Store - 用户认证与权限信息
 * @description 管理用户登录态、用户信息、角色与权限码列表
 * @module store/modules/user
 *
 * 职责：
 *  - 登录（含 2FA 二次验证场景）/登出
 *  - 拉取并缓存用户信息、角色、权限码
 *  - 提供权限判定能力 hasPermission，供路由守卫与 v-permission 指令使用
 *  - 与 permission store 联动：登出/切换账号时同步清空动态路由
 *
 * 注意：token 同步持久化到 localStorage（utils/auth），刷新页面后仍可恢复登录态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, getUserInfoApi } from '@/api/user'
import type { LoginParams, LoginResult, UserInfo } from '@/api/user/types'
import { removeToken, setToken, getToken } from '@/utils/auth'
import { usePermissionStore } from '@/store/modules/permission'

export const useUserStore = defineStore('user', () => {
  /** Access Token（同步持久化到 localStorage） */
  const token = ref<string>(getToken() || '')
  /** Refresh Token，用于无感续期（暂未启用） */
  const refreshToken = ref<string>('')
  /** 当前用户信息，null 表示未登录或未加载 */
  const userInfo = ref<UserInfo | null>(null)
  /** 角色码列表（如 ['admin', 'pm']） */
  const roles = ref<string[]>([])
  /** 权限码列表（如 ['system:user:list', '*:*:*']），含通配符表示超管 */
  const permissions = ref<string[]>([])

  /** 是否已登录（仅判断 token 是否存在，不校验有效性） */
  const isLoggedIn = computed(() => !!token.value)
  /** 用户名（userInfo 加载前为空串） */
  const username = computed(() => userInfo.value?.username || '')
  /** 真实姓名（用于头部展示） */
  const realName = computed(() => userInfo.value?.realName || '')

  /**
   * 登录（支持 2FA）
   *
   * @param params - 登录参数（账号密码 + 可选验证码）
   * @returns LoginResult，当 mfaRequired 为 true 时不存 token
   */
  async function login(params: LoginParams): Promise<LoginResult> {
    const { data } = await loginApi(params)
    if (data.mfaRequired && !data.mfaPassed) {
      // 2FA 二次验证未通过，暂不存 token，由上层触发 2FA 校验
      token.value = ''
      refreshToken.value = ''
      return data
    }
    const tk = data.accessToken || data.token || ''
    const rt = data.refreshToken || ''
    token.value = tk
    refreshToken.value = rt
    setToken(tk, rt)
    return data
  }

  /**
   * 拉取用户信息
   *
   * 由路由守卫在首次进入受保护路由时触发，结果缓存到 userInfo/roles/permissions
   */
  async function fetchUserInfo(): Promise<void> {
    const { data } = await getUserInfoApi()
    userInfo.value = data
    roles.value = data.roles || []
    permissions.value = data.permissions || []
  }

  /**
   * 登出
   *
   * 无论后端 logoutApi 是否成功，都会调用 clearAuth 清空本地认证信息
   */
  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } finally {
      clearAuth()
    }
  }

  /**
   * 清除认证信息
   *
   * 切换账号或登出时，必须同步清空已注册的动态路由，避免：
   *   1. 上一个账号的菜单残留到新账号
   *   2. 路由 name 冲突导致 addRoute 失败
   */
  function clearAuth(): void {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    removeToken()
    try {
      const permissionStore = usePermissionStore()
      permissionStore.reset()
    } catch (_e) {
      // permission store 未初始化时忽略（如应用初次启动失败）
    }
  }

  /**
   * 判断是否拥有指定权限
   *
   * @param perm - 权限码，三段式 module:resource:action
   * @returns true 表示拥有权限；超管通配符 *:*:* 直接放行
   */
  function hasPermission(perm: string): boolean {
    if (permissions.value.includes('*:*:*')) return true
    return permissions.value.includes(perm)
  }

  return {
    token,
    refreshToken,
    userInfo,
    roles,
    permissions,
    isLoggedIn,
    username,
    realName,
    login,
    fetchUserInfo,
    logout,
    clearAuth,
    hasPermission,
  }
})
