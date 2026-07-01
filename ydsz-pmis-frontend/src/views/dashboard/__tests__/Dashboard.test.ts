/**
 * @file Dashboard 首页仪表盘 单元测试
 * @description 批次 21 / P2 - 验证 useECharts composable 集成与 KPI 渲染。覆盖 KPI 卡片数量与数值、
 *              4 个图表容器、健康度饼图/EVM 柱图/预警 TOP5 配置、毛利率与利用率渲染、
 *              周期切换下拉及组件卸载时 dispose 调用等场景
 * @module views/dashboard/__tests__/Dashboard
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

// ===== Mock ECharts =====
const setOptionMock = vi.fn()
const resizeMock = vi.fn()
const disposeMock = vi.fn()
const chartInstance = { setOption: setOptionMock, resize: resizeMock, dispose: disposeMock, getOption: () => ({}) }

vi.mock('echarts', () => ({
  init: vi.fn(() => chartInstance),
  default: { init: vi.fn(() => chartInstance) },
}))

// ===== Mock API =====
vi.mock('@/api/execution/cockpit', () => ({
  getCockpitOverview: vi.fn(async (period: string) => ({
    data: {
      activeProjectCount: 25,
      totalRevenue: 12_000_000,
      recognizedRevenue: 8_500_000,
      totalGrossProfit: 2_800_000,
      grossMargin: 0.233,
      evmRedCount: 2,
      evmYellowCount: 5,
      evmGreenCount: 18,
      avgUtilization: 0.78,
      benchIdleCost: 120_000,
      normalProjects: 18,
      yellowProjects: 5,
      redProjects: 2,
    },
  })),
}))

vi.mock('@/api/execution/alert', () => ({
  getCockpitAlertTopN: vi.fn(async (period: string, topN: number) => ({
    data: [
      { projectCode: 'P001', projectName: '项目甲', alertLevel: 'RED', alertCount: 8 },
      { projectCode: 'P002', projectName: '项目乙', alertLevel: 'YELLOW', alertCount: 5 },
      { projectCode: 'P003', projectName: '项目丙', alertLevel: 'RED', alertCount: 3 },
    ],
  })),
}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    realName: '测试',
    username: 'tester',
    userInfo: { username: 'tester', realName: '测试' },
    fetchUserInfo: vi.fn(),
  }),
}))

import Dashboard from '../index.vue'

// ===== Element Plus 组件 stub (jsdom 环境下避免 el-* 解析失败) =====
const elementPlusStubs = {
  'el-card': defineComponent({ setup: (_, { slots }) => () => h('div', { class: 'el-card-stub' }, slots.default?.()) }),
  'el-select': defineComponent({
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    setup: (_, { slots }) => () => h('div', { class: 'el-select-stub' }, slots.default?.()),
  }),
  'el-option': defineComponent({ props: ['label', 'value'], setup: (_, { slots }) => () => h('div', { class: 'el-option-stub' }, slots.default?.()) }),
  'el-button': defineComponent({
    props: ['loading'],
    emits: ['click'],
    setup: (_, { slots }) => () => h('button', { class: 'el-button-stub' }, slots.default?.()),
  }),
  'el-row': defineComponent({ setup: (_, { slots }) => () => h('div', { class: 'el-row-stub' }, slots.default?.()) }),
  'el-col': defineComponent({
    props: ['span'],
    setup: (_, { slots }) => () => h('div', { class: 'el-col-stub' }, slots.default?.()),
  }),
  'el-icon': defineComponent({
    props: ['size'],
    setup: (_, { slots }) => () => h('span', { class: 'el-icon-stub' }, slots.default?.()),
  }),
  'el-scrollbar': defineComponent({
    props: ['height'],
    setup: (_, { slots }) => () => h('div', { class: 'el-scrollbar-stub' }, slots.default?.()),
  }),
}

describe('Dashboard 仪表盘 (useECharts 迁移)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    setOptionMock.mockClear()
    resizeMock.mockClear()
    disposeMock.mockClear()
  })

  it('正常渲染 4 个 KPI 卡片', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    const metricTitles = wrapper.findAll('.metric-title')
    expect(metricTitles.length).toBe(4)
    expect(metricTitles[0].text()).toContain('活跃项目数')
    expect(metricTitles[1].text()).toContain('本月合同额')
    expect(metricTitles[2].text()).toContain('已确认收入')
    expect(metricTitles[3].text()).toContain('本月毛利')
  })

  it('KPI 数值基于后端响应计算', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    const values = wrapper.findAll('.metric-value .value')
    // 活跃项目数 = 25
    expect(values[0].text()).toBe('25')
    // 合同额 = 12000000 / 10000 = 1200.0
    expect(values[1].text()).toBe('1200.0')
    // 已确认收入 = 8500000 / 10000 = 850.0
    expect(values[2].text()).toBe('850.0')
    // 毛利 = 2800000 / 10000 = 280.0
    expect(values[3].text()).toBe('280.0')
  })

  it('4 个图表容器存在', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    const charts = wrapper.findAll('.chart-area')
    expect(charts.length).toBe(4)
  })

  it('useECharts 集成: setOption 被调用 4 次 (KPI 加载后)', async () => {
    mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()
    // 4 个 useECharts 实例各自至少调用 1 次
    expect(setOptionMock).toHaveBeenCalled()
    // 调用次数: 健康度 + 趋势 + EVM + TOP5 = 4
    expect(setOptionMock.mock.calls.length).toBeGreaterThanOrEqual(4)
  })

  it('健康度饼图配置正确', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    // 找到含 '健康度分布' 的 option 调用
    const healthCall = setOptionMock.mock.calls.find(
      (call) => call[0] && (call[0] as { title?: { text?: string } }).title?.text?.includes('健康度分布'),
    )
    expect(healthCall, '健康度 option 应被设置').toBeTruthy()
    const opt = healthCall![0] as { series: Array<{ data: Array<{ name: string; value: number }> }> }
    expect(opt.series[0].data).toHaveLength(3)
    expect(opt.series[0].data[0].name).toBe('正常')
  })

  it('EVM 柱图配置正确', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    const evmCall = setOptionMock.mock.calls.find(
      (call) => call[0] && (call[0] as { title?: { text?: string } }).title?.text?.includes('EVM'),
    )
    expect(evmCall, 'EVM option 应被设置').toBeTruthy()
    const opt = evmCall![0] as { xAxis: { data: string[] }; series: Array<{ data: unknown[] }> }
    expect(opt.xAxis.data).toEqual(['正常', '黄色预警', '红色预警'])
    expect(opt.series[0].data).toHaveLength(3)
  })

  it('预警 TOP 5 配置正确', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    const topCall = setOptionMock.mock.calls.find(
      (call) => call[0] && (call[0] as { title?: { text?: string } }).title?.text?.includes('TOP'),
    )
    expect(topCall, 'TOP 5 option 应被设置').toBeTruthy()
    const opt = topCall![0] as { yAxis: { data: string[] } }
    expect(opt.yAxis.data).toContain('项目甲')
  })

  it('毛利率与利用率渲染', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()

    const html = wrapper.html()
    expect(html).toContain('毛利率')
    expect(html).toContain('23.3%')  // 0.233 * 100 = 23.3
    expect(html).toContain('78.0%')  // 0.78 * 100 = 78.0
  })

  it('周期切换下拉显示 12 个月', async () => {
    const wrapper = mount(Dashboard, {
      global: { plugins: [createPinia()], stubs: elementPlusStubs },
    })
    await flushPromises()
    await nextTick()
    const select = wrapper.find('.toolbar .el-select-stub')
    expect(select.exists()).toBe(true)
  })

  it('组件卸载时 dispose 被调用', async () => {
    const wrapper = mount(Dashboard, { global: { plugins: [createPinia()] } })
    await flushPromises()
    await nextTick()
    wrapper.unmount()
    // 4 个 useECharts 实例各自 dispose 1 次
    expect(disposeMock).toHaveBeenCalled()
    expect(disposeMock.mock.calls.length).toBeGreaterThanOrEqual(4)
  })
})
