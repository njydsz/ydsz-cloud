import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 移动端响应式断点 composable（批次 19 P3-2 落地）
 *
 * 提供 4 个断点检测，对应 Element Plus Grid 断点：
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
export type DeviceType = 'mobile' | 'tablet' | 'desktop' | 'wide'

export interface UseResponsiveReturn {
  /** 是否移动端 (<768px) */
  isMobile: import('vue').Ref<boolean>
  /** 是否平板 (768-1200px) */
  isTablet: import('vue').Ref<boolean>
  /** 是否桌面端 (1200-1920px) */
  isDesktop: import('vue').Ref<boolean>
  /** 是否大屏 (≥1920px) */
  isWide: import('vue').Ref<boolean>
  /** 当前窗口宽度（px） */
  screenWidth: import('vue').Ref<number>
  /** 当前窗口高度（px） */
  screenHeight: import('vue').Ref<number>
  /** 当前设备类型 */
  device: import('vue').Ref<DeviceType>
}

export function useResponsive(): UseResponsiveReturn {
  const screenWidth = ref(window.innerWidth)
  const screenHeight = ref(window.innerHeight)

  const update = () => {
    screenWidth.value = window.innerWidth
    screenHeight.value = window.innerHeight
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', update)
  })

  const isMobile = ref(screenWidth.value < 768)
  const isTablet = ref(screenWidth.value >= 768 && screenWidth.value < 1200)
  const isDesktop = ref(screenWidth.value >= 1200 && screenWidth.value < 1920)
  const isWide = ref(screenWidth.value >= 1920)

  // 响应式更新
  const refresh = () => {
    isMobile.value = screenWidth.value < 768
    isTablet.value = screenWidth.value >= 768 && screenWidth.value < 1200
    isDesktop.value = screenWidth.value >= 1200 && screenWidth.value < 1920
    isWide.value = screenWidth.value >= 1920
  }
  refresh()

  return {
    isMobile,
    isTablet,
    isDesktop,
    isWide,
    screenWidth,
    screenHeight,
    device: ref<'mobile' | 'tablet' | 'desktop' | 'wide'>(
      isMobile.value ? 'mobile' : isTablet.value ? 'tablet' : isWide.value ? 'wide' : 'desktop'
    )
  }
}
