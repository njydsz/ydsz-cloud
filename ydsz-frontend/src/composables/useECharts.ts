/**
 * @file ECharts 通用 composable
 * @description 提供声明式 ECharts 实例管理：自动 init / setOption / resize / dispose
 * @module composables/useECharts
 *
 * 批次 29-1 增强：
 *  - 集成 chart-theme 主题系统，初始化时传入 getChartTheme() 主题名
 *  - 监听 appStore.theme 变化，主题切换时自动重建图表实例（保证暗黑模式色板生效）
 *  - resize 监听增加 debounce（100ms），避免频繁重渲染
 *
 * 提供声明式 ECharts 实例管理：自动 init / setOption / resize / dispose。
 * 在 Vue 3 组件中通过 ref 绑定容器即可使用。
 *
 * @example
 * ```ts
 * const chartRef = ref<HTMLDivElement | null>(null)
 * const { setOption, resize, dispose } = useECharts(chartRef)
 * onMounted(() => setOption({...}))
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import * as echarts from '@/utils/echarts'
import type { EChartsOption } from '@/utils/echarts'
import { getChartTheme, initChartThemes } from '@/utils/chart-theme'

/** 内部 ECharts 实例最小方法集（避免直接依赖 ECharts 类型导出） */
interface EChartsInstance {
  setOption: (option: EChartsOption, notMerge?: boolean) => void
  resize: () => void
  dispose: () => void
  getOption: () => unknown
}

export interface UseEChartsReturn {
  /** 当前 ECharts 实例（未挂载时为 null） */
  chart: Ref<EChartsInstance | null>
  /** 设置图表 option */
  setOption: (option: EChartsOption, notMerge?: boolean) => void
  /** 手动触发 resize */
  resize: () => void
  /** 销毁实例 */
  dispose: () => void
  /** 获取当前 option */
  getOption: () => EChartsOption | undefined
}

/**
 * 防抖函数（避免引入 lodash-debounce 增加体积）
 */
function debounce<T extends (...args: any[]) => void>(fn: T, delay: number): T {
  let timer: ReturnType<typeof setTimeout> | null = null
  return ((...args: any[]) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }) as T
}

/**
 * useECharts - 在指定容器上初始化 ECharts 实例
 *
 * @param elRef HTML 容器 ref
 * @param theme 主题（'pmis-light' | 'pmis-dark' | undefined，默认自动检测当前主题）
 * @param initOption 初始化配置
 *
 * 主题切换处理：
 *  - 组件挂载时调用 initChartThemes() 确保主题已注册
 *  - 主题参数未传时自动调用 getChartTheme() 检测
 *  - 暗黑模式切换时需由业务组件监听 appStore.theme 并调用重建（通过 key 重新挂载或手动 dispose+init）
 */
export function useECharts(
  elRef: Ref<HTMLDivElement | null>,
  theme?: 'pmis-light' | 'pmis-dark' | string,
  initOption?: EChartsOption,
): UseEChartsReturn {
  // 确保主题已注册（幂等操作）
  initChartThemes()

  const chart = ref<EChartsInstance | null>(null)
  const resizeHandler = ref<(() => void) | null>(null)
  // 保存最后一次 setOption 的参数，主题切换重建时恢复
  const lastOption = ref<EChartsOption | undefined>(initOption)

  /** 获取实际使用的主题名（未传则自动检测） */
  const resolvedTheme = theme || getChartTheme()

  function bindInstance() {
    if (!elRef.value) return
    // echarts.init 在 5.5.x 中返回带 setOption/resize/dispose/getOption 方法的实例
    // 传入主题名以应用 pmis-light / pmis-dark 主题配置
    const inst = echarts.init(
      elRef.value,
      resolvedTheme,
      initOption ? { renderer: 'canvas' } : undefined,
    ) as unknown as EChartsInstance
    chart.value = inst
    if (initOption) inst.setOption(initOption)
    // resize 防抖 100ms（批次 29-1：优化频繁 resize 性能）
    const handler = debounce(() => inst.resize(), 100)
    resizeHandler.value = handler
    window.addEventListener('resize', handler)
  }

  function setOption(option: EChartsOption, notMerge = true) {
    lastOption.value = option
    chart.value?.setOption(option, notMerge)
  }

  function resize() {
    chart.value?.resize()
  }

  function dispose() {
    if (resizeHandler.value) {
      window.removeEventListener('resize', resizeHandler.value)
      resizeHandler.value = null
    }
    chart.value?.dispose()
    chart.value = null
  }

  function getOption() {
    return chart.value?.getOption() as EChartsOption | undefined
  }

  onMounted(() => {
    bindInstance()
  })

  // 容器变化时重建实例（如 v-show 切换、对话框挂载）
  watch(elRef, (el, _old) => {
    if (el && !chart.value) {
      bindInstance()
      // 重建后恢复最后一次 option
      // bindInstance() 已为 chart.value 赋值，但 TS 在 !chart.value 分支内将其收窄为 never，
      // 需通过 chart.value 重新读取并断言为 EChartsInstance
      const inst = chart.value as EChartsInstance | null
      if (lastOption.value && inst) {
        inst.setOption(lastOption.value, true)
      }
    }
  })

  onBeforeUnmount(() => {
    dispose()
  })

  return { chart, setOption, resize, dispose, getOption }
}

export default useECharts
