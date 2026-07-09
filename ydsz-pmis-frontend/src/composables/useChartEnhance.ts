/**
 * @file 图表增强 composable
 * @description 为 ECharts 图表提供统一的增强功能：
 *   - 数据导出（CSV/PNG）
 *   - 图表下钻（drill-down）
 *   - 数据对比（同比/环比）
 *   - 自适应缩放
 * @module composables/useChartEnhance
 *
 * 用法：
 *   const { exportChart, exportCSV, drillDown, compareData } = useChartEnhance()
 *
 *   // 导出图表为 PNG
 *   exportChart(chartInstance, '项目利润趋势')
 *
 *   // 导出数据为 CSV
 *   exportCSV(data, '项目利润数据')
 *
 *   // 下钻
 *   chartInstance.on('click', (params) => drillDown(params, '/project/detail'))
 */
import { ref } from 'vue'

/** CSV 导出选项 */
export interface CsvExportOptions {
  /** 文件名（不含扩展名） */
  filename: string
  /** 表头映射: { 字段名: 显示名 } */
  headers?: Record<string, string>
  /** 编码，默认 UTF-8 with BOM */
  encoding?: string
}

export function useChartEnhance() {
  /** 是否正在导出 */
  const exporting = ref(false)

  /**
   * 导出 ECharts 图表为 PNG
   *
   * @param chart ECharts 实例
   * @param filename 文件名（不含扩展名）
   * @param pixelRatio 像素比，默认 2（高清）
   */
  async function exportChart(
    chart: { getDataURL: (opts: { type: string; pixelRatio: number; backgroundColor: string }) => string },
    filename: string,
    pixelRatio = 2,
  ): Promise<void> {
    exporting.value = true
    try {
      const url = chart.getDataURL({
        type: 'png',
        pixelRatio,
        backgroundColor: '#fff',
      })
      const link = document.createElement('a')
      link.href = url
      link.download = `${filename}.png`
      link.click()
    } finally {
      exporting.value = false
    }
  }

  /**
   * 导出数据为 CSV 文件
   *
   * @param data 数据数组
   * @param options 导出选项
   */
  function exportCSV<T extends Record<string, unknown>>(
    data: T[],
    options: CsvExportOptions,
  ): void {
    if (!data.length) return

    const { filename, headers, encoding = 'utf-8' } = options

    // 获取所有字段名
    const keys = Object.keys(data[0])

    // 构建表头
    const headerRow = keys
      .map((k) => {
        const display = headers?.[k] || k
        // CSV 转义：包含逗号/引号/换行的用双引号包裹
        return `"${display.replace(/"/g, '""')}"`
      })
      .join(',')

    // 构建数据行
    const dataRows = data.map((row) =>
      keys
        .map((k) => {
          const val = row[k]
          if (val === null || val === undefined) return ''
          const str = typeof val === 'object' ? JSON.stringify(val) : String(val)
          return `"${str.replace(/"/g, '""')}"`
        })
        .join(','),
    )

    // 拼接 CSV 内容
    const csv = [headerRow, ...dataRows].join('\n')

    // 添加 BOM 头（确保 Excel 正确识别 UTF-8 编码）
    const BOM = '\uFEFF'
    const blob = new Blob([BOM + csv], { type: `text/csv;charset=${encoding}` })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${filename}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  /**
   * 图表下钻导航
   *
   * @param params ECharts 点击事件参数
   * @param route 跳转路由
   * @param router Vue Router 实例
   */
  function drillDown(
    params: { name?: string; value?: number; data?: Record<string, unknown> },
    route: string,
    router: { push: (route: string | object) => Promise<void> },
  ): void {
    if (!params?.name) return
    const query: Record<string, string> = { drillDown: params.name }
    if (params.value !== undefined) query.value = String(params.value)
    if (params.data?.id) query.id = String(params.data.id)
    router.push({ path: route, query })
  }

  /**
   * 计算同比/环比数据
   *
   * @param current 当前值
   * @param previous 上期值
   * @returns { change: 绝对变化, percent: 百分比变化, trend: 'up' | 'down' | 'flat' }
   */
  function compareData(
    current: number,
    previous: number,
  ): { change: number; percent: number; trend: 'up' | 'down' | 'flat' } {
    const change = current - previous
    const percent = previous !== 0 ? (change / Math.abs(previous)) * 100 : 0
    const trend = change > 0 ? 'up' : change < 0 ? 'down' : 'flat'
    return { change, percent: Math.round(percent * 100) / 100, trend }
  }

  /**
   * 格式化数字（千分位 + 小数位）
   *
   * @param value 数值
   * @param decimals 小数位数，默认 2
   * @param unit 单位
   */
  function formatNumber(value: number, decimals = 2, unit = ''): string {
    const formatted = value.toLocaleString('zh-CN', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    })
    return unit ? `${formatted} ${unit}` : formatted
  }

  return {
    exporting,
    exportChart,
    exportCSV,
    drillDown,
    compareData,
    formatNumber,
  }
}
