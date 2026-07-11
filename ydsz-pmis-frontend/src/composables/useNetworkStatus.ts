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

const isOnline = ref(navigator.onLine)
const wasOffline = ref(false)

let messageInstance: { close: () => void } | null = null

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
