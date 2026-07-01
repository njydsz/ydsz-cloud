/**
 * @file ECharts 通用 composable
 * @description 提供声明式 ECharts 实例管理：自动 init / setOption / resize / dispose
 * @module composables/useECharts
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

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
 * useECharts - 在指定容器上初始化 ECharts 实例
 *
 * @param elRef HTML 容器 ref
 * @param theme 主题（'dark' | 'light' | undefined）
 * @param initOption 初始化配置
 */
export function useECharts(
  elRef: Ref<HTMLDivElement | null>,
  theme?: 'light' | 'dark' | string,
  initOption?: EChartsOption,
): UseEChartsReturn {
  const chart = ref<EChartsInstance | null>(null)
  const resizeHandler = ref<(() => void) | null>(null)

  function bindInstance() {
    if (!elRef.value) return
    // echarts.init 在 5.5.x 中返回带 setOption/resize/dispose/getOption 方法的实例
    const inst = echarts.init(elRef.value, theme, initOption ? { renderer: 'canvas' } : undefined) as unknown as EChartsInstance
    chart.value = inst
    if (initOption) inst.setOption(initOption)
    const handler = () => inst.resize()
    resizeHandler.value = handler
    window.addEventListener('resize', handler)
  }

  function setOption(option: EChartsOption, notMerge = true) {
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
    }
  })

  onBeforeUnmount(() => {
    dispose()
  })

  return { chart, setOption, resize, dispose, getOption }
}

export default useECharts
