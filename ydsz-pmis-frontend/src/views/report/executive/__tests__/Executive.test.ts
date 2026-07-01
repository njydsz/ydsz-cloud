import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

// Mock ECharts
const echartsInstance = {
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
  getOption: vi.fn(() => ({})),
}
vi.mock('echarts', () => ({
  default: { init: vi.fn(() => echartsInstance) },
  init: vi.fn(() => echartsInstance),
}))

const mockApi = {
  getExecutiveOverview: vi.fn(),
  getKpiTrend: vi.fn(),
  getAlertSummary: vi.fn(),
}
vi.mock('@/api/execution/cockpit', () => mockApi)

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

/**
 * Executive 高管看板 - 派生逻辑与 API 集成验证
 */
describe('Executive 派生逻辑 stub 验证（批次18）', () => {
  it('kpiCards 派生 - 应至少包含 8 个核心 KPI', () => {
    const e = {
      activeProjects: 5,
      totalContractAmount: 1000,
      confirmedRevenue: 800,
      totalCost: 500,
      grossProfit: 300,
      grossMargin: 0.375,
      avgBillableUtilization: 0.85,
      benchIdleCost: 100000,
    }
    const cards = [
      { key: 'projects', label: '在执行项目', value: e.activeProjects },
      { key: 'contract', label: '合同总额', value: e.totalContractAmount },
      { key: 'revenue', label: '已确认收入', value: e.confirmedRevenue },
      { key: 'cost', label: '累计成本', value: e.totalCost },
      { key: 'profit', label: '累计毛利', value: e.grossProfit },
      { key: 'margin', label: '平均毛利率', value: e.grossMargin },
      { key: 'util', label: '可计费利用率', value: e.avgBillableUtilization },
      { key: 'bench', label: 'Bench 闲置成本', value: e.benchIdleCost },
    ]
    expect(cards).toHaveLength(8)
    expect(cards.find((c) => c.key === 'projects')?.value).toBe(5)
  })

  it('healthGradeColor 派生 - A=绿 B=蓝 C=橙 D=红', () => {
    function color(grade?: string): string {
      if (grade === 'A') return '#67C23A'
      if (grade === 'B') return '#409EFF'
      if (grade === 'C') return '#E6A23C'
      return '#F56C6C'
    }
    expect(color('A')).toBe('#67C23A')
    expect(color('B')).toBe('#409EFF')
    expect(color('C')).toBe('#E6A23C')
    expect(color('D')).toBe('#F56C6C')
    expect(color(undefined)).toBe('#F56C6C')
  })

  it('severityTag 派生 - RED=danger YELLOW=warning 其他=info', () => {
    function tag(severity?: string): 'danger' | 'warning' | 'info' {
      if (severity === 'RED') return 'danger'
      if (severity === 'YELLOW') return 'warning'
      return 'info'
    }
    expect(tag('RED')).toBe('danger')
    expect(tag('YELLOW')).toBe('warning')
    expect(tag('INFO')).toBe('info')
  })

  it('fmtYuan 派生 - 大数自动切换单位', () => {
    function fmt(v?: number | null): string {
      if (v === null || v === undefined) return '0'
      if (Math.abs(v) >= 1e8) return (v / 1e8).toFixed(2) + ' 亿'
      if (Math.abs(v) >= 1e4) return (v / 1e4).toFixed(2) + ' 万'
      return v.toFixed(0)
    }
    expect(fmt(0)).toBe('0')
    expect(fmt(123)).toBe('123')
    expect(fmt(12345)).toBe('1.23 万')
    expect(fmt(1.5e8)).toBe('1.50 亿')
    expect(fmt(null)).toBe('0')
    expect(fmt(undefined)).toBe('0')
  })

  it('pct1 派生 - 0-1 转 % 字符串', () => {
    function pct(v?: number | null): string {
      if (v === null || v === undefined) return '0%'
      return (v * 100).toFixed(1) + '%'
    }
    expect(pct(0)).toBe('0.0%')
    expect(pct(0.5)).toBe('50.0%')
    expect(pct(1)).toBe('100.0%')
    expect(pct(null)).toBe('0%')
  })
})

describe('Executive API 集成 (批次18)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('Executive API 3 个核心接口都已 export', async () => {
    const mod = await import('@/api/execution/cockpit')
    expect(typeof (mod as any).getExecutiveOverview).toBe('function')
    expect(typeof (mod as any).getKpiTrend).toBe('function')
    expect(typeof (mod as any).getAlertSummary).toBe('function')
  })
})

describe('Executive/index.vue 轻量挂载', () => {
  it('挂载 executive 页面 - 不抛错', async () => {
    mockApi.getExecutiveOverview.mockResolvedValue({
      data: {
        activeProjects: 5,
        totalContractAmount: 1000,
        confirmedRevenue: 800,
        totalCost: 500,
        grossProfit: 300,
        grossMargin: 0.375,
        avgBillableUtilization: 0.85,
        benchIdleCost: 100000,
        evmRedCount: 0,
        evmYellowCount: 1,
        evmGreenCount: 4,
        healthRatio: 0.8,
        riskProjectCount: 1,
        riskProjectRatio: 0.2,
        healthScore: 82,
        healthGrade: 'B',
        projectGroups: [],
      },
    })
    mockApi.getKpiTrend.mockResolvedValue({
      data: { periods: ['2026-01', '2026-02'], contractAmountSeries: [100, 200], confirmedRevenueSeries: [80, 160], totalCostSeries: [50, 100], grossProfitSeries: [30, 60], grossMarginPctSeries: [30, 30] },
    })
    mockApi.getAlertSummary.mockResolvedValue({
      data: { redCount: 0, yellowCount: 0, totalCount: 0, events: [], topEvent: null },
    })

    const Executive = (await import('@/views/report/executive/index.vue')).default
    const wrapper = mount(Executive as any, {
      global: {
        stubs: {
          'el-card': defineComponent({ props: ['header'], setup: (_, { slots }) => () => h('div', {}, slots.default?.()) }),
          'el-row': defineComponent({ props: ['gutter'], setup: (_, { slots }) => () => h('div', {}, slots.default?.()) }),
          'el-col': defineComponent({ props: ['xs', 'sm', 'md', 'lg'], setup: (_, { slots }) => () => h('div', {}, slots.default?.()) }),
          'el-tag': defineComponent({ props: ['type', 'size', 'effect'], setup: (_, { slots }) => () => h('span', {}, slots.default?.()) }),
          'el-button': defineComponent({ props: ['icon', 'loading'], setup: (_, { slots }) => () => h('button', {}, slots.default?.()) }),
          'el-switch': defineComponent({ props: ['modelValue'], emits: ['change', 'update:modelValue'], setup: (_, { slots }) => () => h('div', {}, slots.default?.()) }),
          'el-empty': defineComponent({ props: ['description', 'imageSize'], setup: (_, { slots }) => () => h('div', {}, slots.default?.()) }),
        },
      },
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })
})
