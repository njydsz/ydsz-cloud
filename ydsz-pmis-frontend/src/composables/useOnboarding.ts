/**
 * @file 首屏功能引导 composable
 * @description 基于 driver.js 实现新用户首屏引导：
 *   - 首次登录后自动展示功能引导
 *   - 引导步骤可配置（侧边栏/搜索/通知/主题切换等）
 *   - 引导完成后记录到 localStorage，不再重复展示
 *   - 支持手动触发"再看一次"
 * @module composables/useOnboarding
 *
 * 依赖：driver.js (^1.3.1)
 * 安装：pnpm add driver.js
 */
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/modules/user'

/** localStorage key 前缀 */
const ONBOARDING_KEY_PREFIX = 'pmis-onboarding-'

/** 引导步骤定义 */
export interface OnboardingStep {
  /** 目标元素 CSS 选择器 */
  element: string
  /** 引导标题 */
  title: string
  /** 引导描述 */
  description: string
  /** 弹出位置 */
  side?: 'top' | 'bottom' | 'left' | 'right'
  /** 对齐方式 */
  align?: 'start' | 'center' | 'end'
}

/** 默认引导步骤 */
const DEFAULT_STEPS: OnboardingStep[] = [
  {
    element: '.sidebar-container, .app-sidebar',
    title: '功能菜单',
    description: '在这里可以访问所有功能模块，包括项目管理、执行管理、审批中心等。',
    side: 'right',
    align: 'center',
  },
  {
    element: '.search-trigger, [aria-label*="搜索"]',
    title: '全局搜索',
    description: '使用 Ctrl+K 快捷键随时打开全局搜索，快速查找项目、合同、审批等。',
    side: 'bottom',
    align: 'start',
  },
  {
    element: '.notification-bell, [class*="notification"]',
    title: '消息通知',
    description: '查看待办审批、系统通知和业务提醒，重要事项不错过。',
    side: 'bottom',
    align: 'end',
  },
  {
    element: '[aria-label*="主题"], [class*="theme"]',
    title: '主题切换',
    description: '点击切换浅色/暗色主题，保护视力更舒适。',
    side: 'bottom',
    align: 'end',
  },
  {
    element: '.user-info, .el-dropdown',
    title: '个人中心',
    description: '点击头像进入安全设置或退出登录。',
    side: 'bottom',
    align: 'end',
  },
]

/**
 * 首屏引导 composable
 *
 * @param steps 自定义引导步骤（不传则使用默认步骤）
 * @param autoStart 是否自动启动（首次登录时），默认 true
 */
export function useOnboarding(
  steps: OnboardingStep[] = DEFAULT_STEPS,
  autoStart = true,
) {
  const userStore = useUserStore()
  /** 引导是否正在运行 */
  const isRunning = ref(false)
  /** 引导是否已完成 */
  const isCompleted = ref(false)

  /** 获取当前用户的 onboarding key */
  function getStorageKey(): string {
    const userId = userStore.userInfo?.id || 'guest'
    return `${ONBOARDING_KEY_PREFIX}${userId}`
  }

  /** 检查是否已完成引导 */
  function checkCompleted(): boolean {
    try {
      return localStorage.getItem(getStorageKey()) === 'completed'
    } catch {
      return false
    }
  }

  /** 标记引导完成 */
  function markCompleted(): void {
    try {
      localStorage.setItem(getStorageKey(), 'completed')
    } catch {
      // localStorage 不可用时静默降级
    }
    isCompleted.value = true
  }

  /**
   * 启动引导
   * 动态导入 driver.js 避免打包体积
   */
  async function start(): Promise<void> {
    if (isRunning.value) return

    try {
      const { driver } = await import('driver.js')
      await import('driver.js/dist/driver.css')

      const driverObj = driver({
        showProgress: true,
        steps: steps.map((s) => ({
          element: s.element,
          popover: {
            title: s.title,
            description: s.description,
            side: s.side || 'bottom',
            align: s.align || 'start',
          },
        })),
        onDestroyed: () => {
          isRunning.value = false
          markCompleted()
        },
        onDeselected: () => {
          // 用户跳过引导也算完成
        },
      })

      isRunning.value = true
      driverObj.drive()
    } catch (e) {
      console.warn('[useOnboarding] driver.js 加载失败:', e)
      isRunning.value = false
    }
  }

  /** 重置引导（允许再次展示） */
  function reset(): void {
    try {
      localStorage.removeItem(getStorageKey())
    } catch {
      // ignore
    }
    isCompleted.value = false
  }

  // 自动启动：首次登录且未完成引导时自动展示
  onMounted(() => {
    if (autoStart && !checkCompleted()) {
      // 延迟 1s 等待页面渲染完成
      setTimeout(() => {
        if (!checkCompleted()) {
          start()
        }
      }, 1000)
    }
    isCompleted.value = checkCompleted()
  })

  return {
    isRunning,
    isCompleted,
    start,
    reset,
  }
}
