/**
 * @file 图表数据下钻 composable
 * @description 提供 ECharts 图表点击下钻的通用逻辑：
 *  - 管理下钻维度切换
 *  - 记录下钻路径（面包屑）
 *  - 下载数据并渲染下级图表
 *  - 返回上一级 / 返回根级
 *
 * @example
 * ```ts
 * const { drillPath, drillData, drillLoading, drill, goBack, reset } = useChartDrillDown({
 *   rootDimension: 'projectType',
 *   fetcher: async (dimension, params) => {
 *     const { data } = await drillByDimension(dimension, params)
 *     return data
 *   },
 * })
 * ```
 *
 * @module composables/useChartDrillDown
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
import { ref, computed, type Ref } from 'vue'
import { logger } from '@/utils/logger'

/** 下钻路径节点 */
export interface DrillPathNode {
  /** 维度标识（如 'projectType', 'dept', 'customer'） */
  dimension: string
  /** 维度值（如 '软件交付' / '研发部'） */
  label: string
  /** 下钻到此维度时的参数 */
  params?: Record<string, unknown>
}

/** 下钻 fetcher 函数类型 */
export type DrillFetcher = (
  dimension: string,
  params?: Record<string, unknown>,
) => Promise<Record<string, unknown>[]>

/** useChartDrillDown 配置 */
export interface UseChartDrillDownOptions {
  /** 根级维度标识 */
  rootDimension: string
  /** 数据获取函数 */
  fetcher: DrillFetcher
  /** 根级参数 */
  rootParams?: Record<string, unknown>
}

/**
 * 图表数据下钻 composable
 *
 * @param options 配置选项
 * @returns 下钻状态与操作方法
 */
export function useChartDrillDown(options: UseChartDrillDownOptions): {
  /** 当前下钻路径（面包屑） */
  drillPath: Ref<DrillPathNode[]>
  /** 当前下钻数据 */
  drillData: Ref<Record<string, unknown>[]>
  /** 下钻加载中 */
  drillLoading: Ref<boolean>
  /** 当前是否处于下钻状态（非根级） */
  isDrilling: Ref<boolean>
  /** 当前维度 */
  currentDimension: Ref<string>
  /** 面包屑标签列表 */
  breadcrumb: Ref<string[]>
  /** 下钻到指定维度 */
  drill: (dimension: string, label: string, params?: Record<string, unknown>) => Promise<void>
  /** 返回上一级 */
  goBack: () => Promise<void>
  /** 返回根级 */
  reset: () => Promise<void>
  /** 刷新当前层级 */
  refresh: () => Promise<void>
} {
  const drillPath = ref<DrillPathNode[]>([
    { dimension: options.rootDimension, label: options.rootDimension, params: options.rootParams },
  ])
  const drillData = ref<Record<string, unknown>[]>([])
  const drillLoading = ref(false)

  const isDrilling = computed(() => drillPath.value.length > 1)
  const currentDimension = computed(() => drillPath.value[drillPath.value.length - 1].dimension)
  const breadcrumb = computed(() => drillPath.value.map((n) => n.label))

  /** 加载当前层级数据 */
  async function loadCurrentLevel() {
    const current = drillPath.value[drillPath.value.length - 1]
    drillLoading.value = true
    try {
      const data = await options.fetcher(current.dimension, current.params)
      drillData.value = data || []
    } catch (e) {
      logger.warn('[useChartDrillDown]', '下钻数据加载失败', e)
      drillData.value = []
    } finally {
      drillLoading.value = false
    }
  }

  /** 下钻到指定维度 */
  async function drill(dimension: string, label: string, params?: Record<string, unknown>) {
    drillPath.value.push({ dimension, label, params })
    await loadCurrentLevel()
  }

  /** 返回上一级 */
  async function goBack() {
    if (drillPath.value.length > 1) {
      drillPath.value.pop()
      await loadCurrentLevel()
    }
  }

  /** 返回根级 */
  async function reset() {
    drillPath.value = [{ dimension: options.rootDimension, label: options.rootDimension, params: options.rootParams }]
    await loadCurrentLevel()
  }

  /** 刷新当前层级 */
  async function refresh() {
    await loadCurrentLevel()
  }

  return {
    drillPath,
    drillData,
    drillLoading,
    isDrilling,
    currentDimension,
    breadcrumb,
    drill,
    goBack,
    reset,
    refresh,
  }
}

export default useChartDrillDown
