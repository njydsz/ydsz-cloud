/**
 * @file 水印 composable
 * @description
 *   根据当前登录用户信息自动创建/移除页面水印。
 *
 *   行为：
 *     - 用户登录（userInfo 非空）：创建水印，内容为「真实姓名/用户名 + 当前时间」
 *     - 用户登出（userInfo 为空）：移除水印，避免跨用户信息残留
 *     - 用户信息变化：更新水印文本，避免删除重建闪烁
 *
 *   用法：在 App.vue 根组件 setup 中调用一次即可
 *
 * @example
 * ```ts
 * // App.vue
 * import { useWatermark } from '@/composables/useWatermark'
 * useWatermark()
 * ```
 *
 * @module composables/useWatermark
 * @since 1.6.0
 */
import { watch, onBeforeUnmount } from 'vue'
import dayjs from 'dayjs'
import { useUserStore } from '@/store/modules/user'
import type { UserInfo } from '@/api/user/types'
import { createWatermark, removeWatermark, updateWatermark } from '@/utils/watermark'

/**
 * 构建水印文本
 * 第一行：真实姓名（优先）或用户名
 * 第二行：当前时间（YYYY-MM-DD HH:mm）
 */
function buildWatermarkText(userInfo: UserInfo): string {
  const name = userInfo.realName || userInfo.username
  const time = dayjs().format('YYYY-MM-DD HH:mm')
  return `${name}\n${time}`
}

/**
 * 水印管理 composable
 *
 * 监听 userStore.userInfo 变化，自动创建/更新/移除水印。
 * 需在根组件 App.vue 中调用一次。
 */
export function useWatermark(): void {
  const userStore = useUserStore()

  watch(
    () => userStore.userInfo,
    (userInfo) => {
      if (userInfo) {
        updateWatermark({ text: buildWatermarkText(userInfo) })
      } else {
        removeWatermark()
      }
    },
    { immediate: true },
  )

  // 组件卸载时移除水印，防止 SPA 热重载或重复挂载时水印残留
  onBeforeUnmount(() => {
    removeWatermark()
  })
}

export { createWatermark, removeWatermark, updateWatermark }
