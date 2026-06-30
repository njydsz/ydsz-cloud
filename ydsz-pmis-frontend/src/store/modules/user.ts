import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, getUserInfoApi } from '@/api/user'
import type { LoginParams, LoginResult, UserInfo } from '@/api/user/types'
import { removeToken, setToken, getToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const refreshToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const realName = computed(() => userInfo.value?.realName || '')

  /**
   * 登录（支持 2FA）
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
   */
  async function fetchUserInfo(): Promise<void> {
    const { data } = await getUserInfoApi()
    userInfo.value = data
    roles.value = data.roles || []
    permissions.value = data.permissions || []
  }

  /**
   * 登出
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
   */
  function clearAuth(): void {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    removeToken()
  }

  /**
   * 判断是否拥有指定权限
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
