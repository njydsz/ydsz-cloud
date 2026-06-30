import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, getUserInfoApi } from '@/api/user'
import type { LoginParams, UserInfo } from '@/api/user/types'
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
   * 登录
   */
  async function login(params: LoginParams): Promise<void> {
    const { data } = await loginApi(params)
    token.value = data.token
    refreshToken.value = data.refreshToken
    setToken(data.token, data.refreshToken)
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
