/**
 * @file 密码过期预警 composable
 * @description 在应用挂载后静默查询当前用户密码过期状态，
 *              若状态为 EXPIRED / EXPIRING_SOON / INITIAL 则返回预警信息。
 * @module composables/usePasswordExpiry
 */
import { ref, onMounted } from 'vue'
import { request } from '@/utils/request'
import { logger } from '@/utils/logger'

/** 密码状态 */
export type PasswordStatus = 'HEALTHY' | 'EXPIRING_SOON' | 'EXPIRED' | 'INITIAL' | 'UNKNOWN'

/** 密码状态响应 */
export interface PasswordStatusResponse {
  status: PasswordStatus
  message: string
  daysRemaining: number
  mustChange: boolean
  lastPwdChangeAt?: string
  pwdChangeCount?: number
  expireDays: number
}

/**
 * 密码过期预警 composable
 *
 * @returns 响应式密码状态与加载标记
 */
export function usePasswordExpiry() {
  const passwordStatus = ref<PasswordStatusResponse | null>(null)
  const loading = ref(false)

  /** 是否需要展示预警横幅 */
  const showWarning = ref(false)

  /**
   * 从后端拉取密码过期状态
   * 静默请求，失败时不影响页面正常使用
   */
  async function fetchPasswordStatus() {
    loading.value = true
    try {
      const { data } = await request<PasswordStatusResponse>({
        url: '/user/passwordStatus',
        method: 'GET',
        silent: true,
      })
      passwordStatus.value = data
      showWarning.value = data?.status === 'EXPIRED' ||
                          data?.status === 'EXPIRING_SOON' ||
                          data?.status === 'INITIAL'
    } catch (e) {
      logger.debug('[usePasswordExpiry]', '获取密码状态失败', e)
      showWarning.value = false
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    fetchPasswordStatus()
  })

  return {
    passwordStatus,
    loading,
    showWarning,
    fetchPasswordStatus,
  }
}
