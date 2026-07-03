/**
 * @file 移动端响应式断点 composable
 * @description 提供 4 个断点检测，对应 Element Plus Grid 断点；自动监听 resize 事件并响应式更新
 * @module composables/useResponsive
 *
 * (批次 19 P3-2 落地)
 *
 * 断点定义：
 * - xs: < 768px  (手机竖屏)
 * - sm: 768-992px  (平板竖屏)
 * - md: 992-1200px (平板横屏/小桌面)
 * - lg: 1200-1920px (标准桌面)
 * - xl: ≥ 1920px (大屏/4K)
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * import { useResponsive } from '@/composables/useResponsive'
 * const { isMobile, isTablet, isDesktop, screenWidth, device } = useResponsive()
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
}

/**
 * 响应式断点 composable 入口
 * @returns 包含 isMobile/isTablet/isDesktop/isWide/screenWidth/screenHeight/device 的响应式对象
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
  }
}
