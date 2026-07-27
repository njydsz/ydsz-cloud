/**
 * @file 网络状态检测 composable
 * @description P1-6: 监听浏览器在线/离线事件，离线时全局提示并阻止请求。
 *   - online/offline 事件监听
 *   - 离线时 ElMessage 全局提示
 *   - 恢复在线时自动提示并允许手动刷新
 *
 * 使用方式：
 *   const { isOnline, wasOffline } = useNetworkStatus()
 *   // 在 App.vue onMounted 中调用即可全局生效
 *
 * @module composables/useNetworkStatus
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import i18n from '@/locales'

/** 是否在线（响应式，与 navigator.onLine 同步） */
const isOnline = ref(navigator.onLine)
/** 是否曾离线（用于恢复时判断是否需要提示） */
const wasOffline = ref(false)

/** 离线时持久提示的 ElMessage 实例引用 */
let messageInstance: { close: () => void } | null = null

/** 网络恢复在线事件处理：关闭离线提示并显示恢复通知 */
function handleOnline() {
  isOnline.value = true
  if (wasOffline.value) {
    // 恢复在线时提示
    if (messageInstance) {
      messageInstance.close()
      messageInstance = null
    }
    ElMessage.success(i18n.global.t('common.networkRestored'))
  }
}

/** 网络断开事件处理：更新在线状态并显示持久提示 */
function handleOffline() {
  isOnline.value = false
  wasOffline.value = true
  // 离线时持久提示
  messageInstance = ElMessage({
    message: i18n.global.t('common.networkOffline'),
    type: 'error',
    duration: 0, // 不自动关闭
    showClose: true,
  })
}

/**
 * 网络状态检测 composable
 *
 * 在 App.vue onMounted 中调用即可全局生效。
 * 离线时自动显示 ElMessage 持久提示，恢复在线时自动提示。
 *
 * @returns `{ isOnline, wasOffline }`
 *   - isOnline: 当前是否在线
 *   - wasOffline: 本次会话中是否曾离线
 */
export function useNetworkStatus() {
  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  })

  onUnmounted(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return {
    isOnline,
    wasOffline,
  }
}
