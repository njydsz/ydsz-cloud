/**
 * @file ECharts 主题与色板统一管理
 * @description 批次 29-1：建立 ECharts 主题注册机制，禁止业务代码硬编码 #xxxxxx 色值。
 *
 * 设计目标：
 *  1. 提供 chartColors 对象，业务代码通过 chartColors.primary / chartColors.success 引用
 *  2. 注册 'pmis-light' / 'pmis-dark' 两套 ECharts 主题，暗黑模式切换时自动重建实例
 *  3. 提供 getChartTheme() 根据 appStore.theme 返回当前主题名
 *  4. 监听主题变化时自动重建已注册的图表实例（通过全局事件总线）
 *
 * 使用方式：
 * ```ts
 * import { chartColors, getChartTheme } from '@/utils/chart-theme'
 * import { useECharts } from '@/composables/useECharts'
 *
 * const chartRef = ref<HTMLDivElement | null>(null)
 * const { setOption, resize } = useECharts(chartRef, getChartTheme())
 *
 * setOption({
 *   series: [{
 *     type: 'pie',
 *     itemStyle: { borderColor: chartColors.borderColor, borderWidth: 2 },
 *     data: [
 *       { name: '正常', value: 18, itemStyle: { color: chartColors.success } },
 *       { name: '预警', value: 7, itemStyle: { color: chartColors.warning } },
 *       { name: '异常', value: 3, itemStyle: { color: chartColors.danger } },
 *     ],
 *   }],
 * })
 * ```
 *
 * @module utils/chart-theme
 * @author ydsz-team
 * @since 1.4.0
 */
import * as echarts from '@/utils/echarts'

/** 图表色板对象（运行时从 CSS 变量读取，自动响应暗黑模式切换） */
export interface ChartColors {
  /** 主品牌色（折线/柱图主序列） */
  primary: string
  /** 成功/正常/绿色预警 */
  success: string
  /** 警告/黄色预警 */
  warning: string
  /** 危险/红色预警 */
  danger: string
  /** 信息/成本序列 */
  info: string
  /** 紫色序列（收入/合同） */
  purple: string
  /** 橙色序列（费用/支出） */
  orange: string
  /** 饼图/环形图分隔线色 */
  borderColor: string
  /** 坐标轴/图例/标题文字色 */
  textColor: string
  /** 网格线色 */
  splitLineColor: string
}

/** 从 CSS 变量读取色值（兼容 SSR 与不支持 getComputedStyle 的环境） */
function readCssVar(name: string): string {
  if (typeof window === 'undefined' || typeof getComputedStyle !== 'function') {
    return ''
  }
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/**
 * 获取当前图表色板（从 CSS 变量实时读取，自动响应暗黑模式切换）
 *
 * 注意：返回的对象是快照，主题切换后需重新调用获取最新值。
 * 在 ECharts option 工厂函数中调用可保证每次 setOption 时读取最新色值。
 */
export function getChartColors(): ChartColors {
  return {
    primary: readCssVar('--chart-color-primary') || '#1677ff',
    success: readCssVar('--chart-color-success') || '#52c41a',
    warning: readCssVar('--chart-color-warning') || '#faad14',
    danger: readCssVar('--chart-color-danger') || '#ff4d4f',
    info: readCssVar('--chart-color-info') || '#909399',
    purple: readCssVar('--chart-color-purple') || '#722ed1',
    orange: readCssVar('--chart-color-orange') || '#fa8c16',
    borderColor: readCssVar('--chart-border-color') || '#ffffff',
    textColor: readCssVar('--chart-text-color') || '#303133',
    splitLineColor: readCssVar('--chart-split-line-color') || '#ebeef5',
  }
}

/**
 * 响应式图表色板（基于 getter，每次访问属性时实时读取 CSS 变量）
 *
 * 适用于 computed option 工厂：option 重新计算时自动读取最新色值。
 * 推荐在 setOption 调用前访问一次以触发读取。
 */
export const chartColors: ChartColors = {
  get primary() {
    return getChartColors().primary
  },
  get success() {
    return getChartColors().success
  },
  get warning() {
    return getChartColors().warning
  },
  get danger() {
    return getChartColors().danger
  },
  get info() {
    return getChartColors().info
  },
  get purple() {
    return getChartColors().purple
  },
  get orange() {
    return getChartColors().orange
  },
  get borderColor() {
    return getChartColors().borderColor
  },
  get textColor() {
    return getChartColors().textColor
  },
  get splitLineColor() {
    return getChartColors().splitLineColor
  },
}

/** ECharts 主题名 */
export type ChartThemeName = 'pmis-light' | 'pmis-dark'

/** 已注册主题标记（避免重复注册） */
let themesRegistered = false

/**
 * 注册 ECharts 主题（pmis-light / pmis-dark）
 *
 * 主题定义包含：色板、坐标轴样式、图例样式、tooltip 样式、文字色、网格线色。
 * 暗黑模式切换时无需重新注册，只需在 init 时传入对应主题名。
 */
export function registerChartThemes(): void {
  if (themesRegistered || typeof echarts.registerTheme !== 'function') {
    return
  }

  // 浅色主题
  const lightColors = getChartColors()
  echarts.registerTheme('pmis-light', {
    color: [
      lightColors.primary,
      lightColors.success,
      lightColors.warning,
      lightColors.danger,
      lightColors.purple,
      lightColors.orange,
      lightColors.info,
    ],
    backgroundColor: 'transparent',
    textStyle: {
      color: lightColors.textColor,
      fontFamily:
        "'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    },
    title: {
      textStyle: { color: lightColors.textColor },
      subtextStyle: { color: lightColors.textColor },
    },
    legend: {
      textStyle: { color: lightColors.textColor },
    },
    tooltip: {
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: lightColors.splitLineColor,
      textStyle: { color: '#303133' },
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: lightColors.splitLineColor } },
      axisTick: { lineStyle: { color: lightColors.splitLineColor } },
      axisLabel: { color: lightColors.textColor },
      splitLine: { show: false },
    },
    valueAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: lightColors.textColor },
      splitLine: { lineStyle: { color: lightColors.splitLineColor } },
    },
  })

  // 暗黑主题（色板已通过 CSS 变量切换为高对比度版本）
  const darkColors = getChartColors()
  echarts.registerTheme('pmis-dark', {
    color: [
      darkColors.primary,
      darkColors.success,
      darkColors.warning,
      darkColors.danger,
      darkColors.purple,
      darkColors.orange,
      darkColors.info,
    ],
    backgroundColor: 'transparent',
    textStyle: {
      color: darkColors.textColor,
      fontFamily:
        "'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    },
    title: {
      textStyle: { color: darkColors.textColor },
      subtextStyle: { color: darkColors.textColor },
    },
    legend: {
      textStyle: { color: darkColors.textColor },
    },
    tooltip: {
      backgroundColor: 'rgba(30, 30, 30, 0.95)',
      borderColor: darkColors.splitLineColor,
      textStyle: { color: '#e5eaf3' },
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: darkColors.splitLineColor } },
      axisTick: { lineStyle: { color: darkColors.splitLineColor } },
      axisLabel: { color: darkColors.textColor },
      splitLine: { show: false },
    },
    valueAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: darkColors.textColor },
      splitLine: { lineStyle: { color: darkColors.splitLineColor } },
    },
  })

  themesRegistered = true
}

/**
 * 获取当前应使用的 ECharts 主题名
 *
 * 通过读取 <html> 是否有 .dark class 判断当前主题。
 * 在 useECharts 初始化时传入此返回值。
 */
export function getChartTheme(): ChartThemeName {
  if (typeof document === 'undefined') return 'pmis-light'
  return document.documentElement.classList.contains('dark') ? 'pmis-dark' : 'pmis-light'
}

/**
 * 初始化图表主题系统
 *
 * 应在应用启动时（main.ts）调用一次：
 *  1. 注册 pmis-light / pmis-dark 主题
 *  2. 监听主题变化，主题切换后需重建图表实例（由 useECharts 的 watch theme 处理）
 */
export function initChartThemes(): void {
  registerChartThemes()
}

export default {
  chartColors,
  getChartColors,
  registerChartThemes,
  getChartTheme,
  initChartThemes,
}
