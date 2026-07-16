/**
 * @file 响应式 composable（断点检测 + rem 自适应）
 * @description 提供两层响应式能力：
 *              1. 断点检测：isMobile/isTablet/isDesktop/isWide/screenWidth/screenHeight/device，对应 Element Plus Grid 断点；
 *              2. rem 自适应（P2-7 大屏适配）：根据视口宽度动态计算 html font-size，配合 _responsive.scss 的 rem() 工具函数，
 *                 让 dashboard/监控/报表类页面在 1920px+ 大屏与 1366px 小屏上均能良好显示。
 * @module composables/useResponsive
 *
 * 断点定义：
 * - xs: < 768px  (手机竖屏)
 * - sm: 768-992px  (平板竖屏)
 * - md: 992-1200px (平板横屏/小桌面)
 * - lg: 1200-1920px (标准桌面)
 * - xl: ≥ 1920px (大屏/4K)
 *
 * rem 自适应公式：
 *   fontSize = (clientWidth / 1920) * 16，限制在 [12, 24]
 *   - 设计稿基准宽度：1920px
 *   - 基准 font-size：16px（1rem = 16px @ 1920px）
 *   - 最小 12px（避免过小）、最大 24px（避免过大）
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * import { useResponsive } from '@/composables/useResponsive'
 * const { isMobile, isTablet, isDesktop, isWide, screenWidth, device, fontSize } = useResponsive()
 * </script>
 *
 * <template>
 *   <el-row v-if="isDesktop" :gutter="20">
 *     <el-col :span="6">...</el-col>
 *   </el-row>
 *   <div v-else>移动端布局</div>
 * </template>
 * ```
 */
import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

// ===== rem 自适应常量 =====
/** 设计稿基准宽度 */
export const DESIGN_WIDTH = 1920
/** 基准 font-size（1rem = 16px @ 1920px） */
export const BASE_FONT_SIZE = 16
/** 最小 font-size（避免过小） */
export const MIN_FONT_SIZE = 12
/** 最大 font-size（避免过大） */
export const MAX_FONT_SIZE = 24

// ===== rem 全局状态（模块级单例，确保 initResponsive 与 useResponsive 共享同一份状态） =====
/** 当前 html font-size（px），由 initResponsive 维护并随窗口 resize 更新 */
const fontSize = ref(BASE_FONT_SIZE)
/** resize 监听器引用，用于解绑与幂等初始化 */
let resizeListener: (() => void) | null = null

/**
 * 根据视口宽度计算并应用 html font-size
 * 公式：fontSize = (clientWidth / 1920) * 16，限制在 [MIN_FONT_SIZE, MAX_FONT_SIZE]
 *
 * 直接操作 document.documentElement.style.fontSize，所有使用 rem 单位的样式将自动响应。
 */
export function updateFontSize(): void {
  const clientWidth = document.documentElement.clientWidth
  // clientWidth 在某些环境（如 SSR 或未布局时）可能为 0，此时回退到基准值避免 NaN
  const safeWidth = clientWidth > 0 ? clientWidth : DESIGN_WIDTH
  const calculated = (safeWidth / DESIGN_WIDTH) * BASE_FONT_SIZE
  fontSize.value = Math.min(Math.max(calculated, MIN_FONT_SIZE), MAX_FONT_SIZE)
  document.documentElement.style.fontSize = `${fontSize.value}px`
}

/**
 * 全局初始化 rem 自适应（方案 B）
 *
 * 在 main.ts 中调用一次即可，不依赖组件生命周期，自行管理 resize 监听。
 * 幂等：重复调用不会叠加多个监听器。
 *
 * @returns 包含 fontSize 的只读响应式状态
 *
 * @example
 * ```ts
 * // main.ts
 * import { initResponsive } from '@/composables/useResponsive'
 * initResponsive()
 * ```
 */
export function initResponsive(): { fontSize: Ref<number> } {
  // 幂等保护：已初始化则直接返回
  if (resizeListener) {
    return { fontSize }
  }
  updateFontSize()
  resizeListener = updateFontSize
  window.addEventListener('resize', resizeListener, { passive: true })
  return { fontSize }
}

/**
 * 卸载全局 rem 自适应（主要用于测试清理或 SSR 切换）
 * 移除 resize 监听器并还原 html style
 */
export function destroyResponsive(): void {
  if (resizeListener) {
    window.removeEventListener('resize', resizeListener)
    resizeListener = null
  }
  document.documentElement.style.fontSize = ''
}

/** 设备类型枚举 */
export type DeviceType = 'mobile' | 'tablet' | 'desktop' | 'wide'

export interface UseResponsiveReturn {
  /** 是否移动端 (<768px) */
  isMobile: Ref<boolean>
  /** 是否平板 (768-1200px) */
  isTablet: Ref<boolean>
  /** 是否桌面端 (1200-1920px) */
  isDesktop: Ref<boolean>
  /** 是否大屏 (≥1920px) */
  isWide: Ref<boolean>
  /** 当前窗口宽度（px） */
  screenWidth: Ref<number>
  /** 当前窗口高度（px） */
  screenHeight: Ref<number>
  /** 当前设备类型 */
  device: Ref<DeviceType>
  /** 当前 html font-size（px），由 initResponsive 全局维护，未初始化时为基准值 */
  fontSize: Ref<number>
}

/**
 * 响应式断点 composable 入口
 *
 * 注意：断点检测基于 window.innerWidth 在组件 onMounted 时读取并监听 resize；
 *      rem font-size 的全局应用请通过 initResponsive() 在 main.ts 初始化。
 *      此处返回的 fontSize 为模块级共享 ref，供组件读取当前基准字号。
 *
 * @returns 包含 isMobile/isTablet/isDesktop/isWide/screenWidth/screenHeight/device/fontSize 的响应式对象
 */
export function useResponsive(): UseResponsiveReturn {
  const screenWidth = ref(window.innerWidth)
  const screenHeight = ref(window.innerHeight)

  // resize 回调：刷新尺寸
  const update = () => {
    screenWidth.value = window.innerWidth
    screenHeight.value = window.innerHeight
  }

  onMounted(() => {
    update()
    // passive: true 提升滚动性能
    window.addEventListener('resize', update, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', update)
  })

  // 使用 computed 自动响应 screenWidth 变化，修复窗口缩放时断点标志位不更新的 bug
  const isMobile = computed(() => screenWidth.value < 768)
  const isTablet = computed(() => screenWidth.value >= 768 && screenWidth.value < 1200)
  const isDesktop = computed(() => screenWidth.value >= 1200 && screenWidth.value < 1920)
  const isWide = computed(() => screenWidth.value >= 1920)
  const device = computed<DeviceType>(() =>
    isMobile.value ? 'mobile' : isTablet.value ? 'tablet' : isWide.value ? 'wide' : 'desktop',
  )

  return {
    isMobile,
    isTablet,
    isDesktop,
    isWide,
    screenWidth,
    screenHeight,
    device,
    // 共享全局 fontSize ref（initResponsive 维护，未初始化时为 BASE_FONT_SIZE）
    fontSize,
  }
}
