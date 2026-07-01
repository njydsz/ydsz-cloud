/**
 * chaos-dashboard 单元测试 (批次 24 P2-2)
 *
 * 覆盖:
 *   - 页面挂载不抛错
 *   - 实验列表渲染 (使用 mock api)
 *   - 启停开关 (toggle) 流程
 *   - Dry-Run 按钮
 *   - 清空历史
 *   - 图表容器存在
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'

import ChaosDashboard from '../index.vue'

// mock ECharts
vi.mock('echarts', () => ({
  init: () => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    getOption: () => ({}),
  }),
}))

// mock API
vi.mock('@/api/chaos', () => ({
  listExperiments: vi.fn(),
  history: vi.fn(),
  toggleExperiment: vi.fn(),
  unregisterExperiment: vi.fn(),
  registerExperiment: vi.fn(),
  dryRun: vi.fn(),
  clearHistory: vi.fn(),
}))

import * as chaosApi from '@/api/chaos'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // 默认 mock 数据
  vi.mocked(chaosApi.listExperiments).mockResolvedValue([
    {
      type: 'LATENCY',
      target: 'ContractService.getContract',
      latencyMs: 500,
      description: '延迟注入',
      enabled: true,
      createdBy: 'admin',
    },
    {
      type: 'EXCEPTION',
      target: 'PaymentService.create',
      errorRate: 0.3,
      description: '异常注入',
      enabled: false,
      createdBy: 'admin',
    },
  ])
  vi.mocked(chaosApi.history).mockResolvedValue([
    { timestamp: Date.now() - 60_000, target: 'PaymentService.create', outcome: 'INJECTED', detail: '已注入' },
    { timestamp: Date.now() - 30_000, target: 'ContractService.getContract', outcome: 'BLOCKED_BY_FLAG', detail: 'flag 关闭' },
  ])
  // 默认消息静默, 避免污染测试输出
  vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
  vi.spyOn(ElMessage, 'error').mockImplementation(() => {})
  vi.spyOn(ElMessage, 'warning').mockImplementation(() => {})
  vi.spyOn(ElMessage, 'info').mockImplementation(() => {})
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
})

describe('chaos-dashboard/index.vue', () => {
  it('挂载不抛错 + 渲染 KPI 与实验表格', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    // KPI
    expect(wrapper.text()).toContain('已注册实验')
    expect(wrapper.text()).toContain('启用中')
    expect(wrapper.text()).toContain('Feature Flag')
    // 表格
    const rows = wrapper.findAll('[data-test="exp-table"] tbody tr')
    expect(rows.length).toBeGreaterThanOrEqual(1)
  })

  it('启停开关触发 toggle API', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    vi.mocked(chaosApi.toggleExperiment).mockResolvedValue(undefined)

    const sw = wrapper.find('[data-test="switch-enabled"]')
    expect(sw.exists()).toBe(true)
    // 触发 el-switch change
    await sw.trigger('change', false)
    await flushPromises()
    expect(chaosApi.toggleExperiment).toHaveBeenCalled()
  })

  it('Dry-Run 按钮调用 dryRun API', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    vi.mocked(chaosApi.dryRun).mockResolvedValue({
      target: 'ContractService.getContract',
      outcome: 'INJECTED',
      error: 'Chaos injected (LATENCY)',
    })
    const btn = wrapper.findAll('[data-test="btn-dry-run"]')[0]
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()
    expect(chaosApi.dryRun).toHaveBeenCalledWith('ContractService.getContract')
  })

  it('注册实验弹窗 + 提交', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    vi.mocked(chaosApi.registerExperiment).mockResolvedValue(undefined)
    // 打开弹窗
    await wrapper.find('[data-test="btn-register"]').trigger('click')
    await flushPromises()
    const dlg = wrapper.find('[data-test="dialog-register"]')
    expect(dlg.exists()).toBe(true)
    // 填 target
    const targetInput = wrapper.find('[data-test="input-target"]')
    await targetInput.setValue('TestService.boom')
    // 提交
    await wrapper.find('[data-test="btn-submit-register"]').trigger('click')
    await flushPromises()
    expect(chaosApi.registerExperiment).toHaveBeenCalled()
  })

  it('清空历史', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    vi.mocked(chaosApi.clearHistory).mockResolvedValue(undefined)
    await wrapper.find('[data-test="btn-clear-history"]').trigger('click')
    await flushPromises()
    expect(chaosApi.clearHistory).toHaveBeenCalled()
  })

  it('图表 canvas 元素存在', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    expect(wrapper.find('[data-test="chart-outcome"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chart-target"]').exists()).toBe(true)
  })

  it('history 表格至少显示 1 行', async () => {
    const wrapper = mount(ChaosDashboard, { attachTo: document.body })
    await flushPromises()
    const rows = wrapper.findAll('[data-test="history-table"] tbody tr')
    expect(rows.length).toBeGreaterThanOrEqual(1)
  })
})
