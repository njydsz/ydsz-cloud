/**
 * @file 会话超时管理 composable
 * @description P1-6: 用户无操作超时自动锁定/登出，倒计时弹窗提示即将超时。
 *   - 监听用户交互事件（鼠标移动、键盘按键、滚动、点击）
 *   - 超时前 5 分钟弹出倒计时弹窗，可选"继续操作"或"立即登出"
 *   - 超时后自动登出并跳转登录页
 *
 * 使用方式：
 *   const { startSessionTimer, stopSessionTimer } = useSessionTimeout()
 *   // 在 App.vue 或 Layout.vue onMounted 中调用 startSessionTimer()
 *
 * @module composables/useSessionTimeout
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import i18n from '@/locales'
import { useUserStore } from '@/store/modules/user'
import router from '@/router'

/** 默认超时时间（30 分钟） */
const DEFAULT_TIMEOUT = 30 * 60 * 1000

/** 超时前提醒时间（5 分钟） */
const WARNING_BEFORE = 5 * 60 * 1000

/** 用户交互事件列表 */
const USER_ACTIVITY_EVENTS = [
  'mousedown',
  'mousemove',
  'keydown',
  'scroll',
  'touchstart',
  'click',
] as const

const lastActivityTime = ref(Date.now())
const showWarning = ref(false)
const remainingSeconds = ref(0)

let timeoutTimer: ReturnType<typeof setTimeout> | null = null
let warningTimer: ReturnType<typeof setTimeout> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 重置活动时间 */
function resetActivity(): void {
  lastActivityTime.value = Date.now()
  if (showWarning.value) {
    // 用户在警告期间有操作，取消警告
    cancelWarning()
    scheduleTimers()
  }
}

/** 取消警告弹窗 */
function cancelWarning(): void {
  showWarning.value = false
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

/** 显示超时警告弹窗 */
function showTimeoutWarning(): void {
  showWarning.value = true
  remainingSeconds.value = Math.floor(WARNING_BEFORE / 1000)

  countdownTimer = setInterval(() => {
    remainingSeconds.value--
    if (remainingSeconds.value <= 0) {
      // 倒计时结束，自动登出
      handleTimeout()
    }
  }, 1000)

  ElMessageBox.confirm(
    i18n.global.t('common.sessionTimeoutWarning', {
      minutes: Math.floor(remainingSeconds.value / 60),
    }),
    i18n.global.t('common.sessionTimeoutTitle'),
    {
      confirmButtonText: i18n.global.t('common.continueSession'),
      cancelButtonText: i18n.global.t('common.logoutNow'),
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: false,
    },
  )
    .then(() => {
      // 用户选择继续
      resetActivity()
      scheduleTimers()
    })
    .catch(() => {
      // 用户选择登出
      handleTimeout()
    })
}

/** 超时处理：自动登出 */
async function handleTimeout(): void {
  cancelWarning()
  clearTimers()
  const userStore = useUserStore()
  await userStore.logout()
  router.push('/login?reason=timeout')
}

/** 调度超时和警告定时器 */
function scheduleTimers(): void {
  clearTimers()

  const warningDelay = DEFAULT_TIMEOUT - WARNING_BEFORE
  const timeoutDelay = DEFAULT_TIMEOUT

  // 警告定时器（超时前 5 分钟）
  warningTimer = setTimeout(() => {
    showTimeoutWarning()
  }, warningDelay)

  // 超时定时器
  timeoutTimer = setTimeout(() => {
    if (!showWarning.value) {
      handleTimeout()
    }
  }, timeoutDelay)
}

/** 清除所有定时器 */
function clearTimers(): void {
  if (warningTimer) {
    clearTimeout(warningTimer)
    warningTimer = null
  }
  if (timeoutTimer) {
    clearTimeout(timeoutTimer)
    timeoutTimer = null
  }
}

export function useSessionTimeout() {
  /** 启动会话超时管理 */
  function startSessionTimer(): void {
    // 监听用户交互
    USER_ACTIVITY_EVENTS.forEach((event) => {
      window.addEventListener(event, resetActivity, { passive: true })
    })

    // 初始调度
    scheduleTimers()
  }

  /** 停止会话超时管理 */
  function stopSessionTimer(): void {
    USER_ACTIVITY_EVENTS.forEach((event) => {
      window.removeEventListener(event, resetActivity)
    })
    clearTimers()
    cancelWarning()
  }

  onUnmounted(() => {
    stopSessionTimer()
  })

  return {
    showWarning,
    remainingSeconds,
    startSessionTimer,
    stopSessionTimer,
  }
}
