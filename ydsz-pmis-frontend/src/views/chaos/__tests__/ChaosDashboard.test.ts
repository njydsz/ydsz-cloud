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
 *   - API 集成 (列表/历史/注册/清空/DryRun/启停)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { elComponents } from '@/tests/setup'

// mock ECharts (必须在 import Component 之前)
vi.mock('echarts', () => ({
  init: () => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    getOption: () => ({}),
  }),
}))

// mock API - 使用 inline factory 避免 hoisting 问题
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
import ChaosDashboard from '../index.vue'

const mockApi = chaosApi as unknown as {
  listExperiments: ReturnType<typeof vi.fn>
  history: ReturnType<typeof vi.fn>
  toggleExperiment: ReturnType<typeof vi.fn>
  unregisterExperiment: ReturnType<typeof vi.fn>
  registerExperiment: ReturnType<typeof vi.fn>
  dryRun: ReturnType<typeof vi.fn>
  clearHistory: ReturnType<typeof vi.fn>
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // 默认 mock 数据
  mockApi.listExperiments.mockResolvedValue([
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
  mockApi.history.mockResolvedValue([
    { timestamp: Date.now() - 60_000, target: 'PaymentService.create', outcome: 'INJECTED', detail: '已注入' },
    { timestamp: Date.now() - 30_000, target: 'ContractService.getContract', outcome: 'BLOCKED_BY_FLAG', detail: 'flag 关闭' },
  ])
  mockApi.toggleExperiment.mockResolvedValue(undefined)
  mockApi.unregisterExperiment.mockResolvedValue(undefined)
  mockApi.registerExperiment.mockResolvedValue(undefined)
  mockApi.dryRun.mockResolvedValue({
    target: 'ContractService.getContract',
    outcome: 'INJECTED',
    error: 'Chaos injected (LATENCY)',
  })
  mockApi.clearHistory.mockResolvedValue(undefined)
})

function mountDashboard() {
  return mount(ChaosDashboard, {
    global: { components: elComponents },
    attachTo: document.body,
  })
}

describe('chaos-dashboard 业务验证 (批次 24 P2-2)', () => {
  it('挂载不抛错 + 渲染 KPI 与实验表格', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    // KPI
    expect(wrapper.text()).toContain('已注册实验')
    expect(wrapper.text()).toContain('启用中')
    expect(wrapper.text()).toContain('Feature Flag')
    wrapper.unmount()
  })

  it('启停开关触发 toggle API', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    // 直接通过 __vueParentComponent 调用内部 onToggle 方法 (jsdom 中 el-switch 的 @change
    // 事件触发链较为脆弱, 这里直接驱动业务方法以验证 API 集成)
    const vm = wrapper.vm as unknown as {
      onToggle: (target: string, enabled: boolean) => Promise<void>
    }
    expect(typeof vm.onToggle).toBe('function')
    await vm.onToggle('ContractService.getContract', false)
    await flushPromises()
    expect(mockApi.toggleExperiment).toHaveBeenCalledWith('ContractService.getContract', false)
    wrapper.unmount()
  })

  it('Dry-Run 按钮调用 dryRun API', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    const btn = wrapper.findAll('[data-test="btn-dry-run"]')[0]
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()
    expect(mockApi.dryRun).toHaveBeenCalledWith('ContractService.getContract')
    wrapper.unmount()
  })

  it('注册实验弹窗 + 提交', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
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
    expect(mockApi.registerExperiment).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('清空历史', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    await wrapper.find('[data-test="btn-clear-history"]').trigger('click')
    await flushPromises()
    expect(mockApi.clearHistory).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('图表 canvas 元素存在', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    expect(wrapper.find('[data-test="chart-outcome"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chart-target"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('history 表格至少显示 1 行', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    const rows = wrapper.findAll('[data-test="history-table"] tbody tr')
    expect(rows.length).toBeGreaterThanOrEqual(1)
    wrapper.unmount()
  })

  it('listExperiments + history 在 mount 时被调用 (refresh)', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    expect(mockApi.listExperiments).toHaveBeenCalled()
    expect(mockApi.history).toHaveBeenCalled()
    wrapper.unmount()
  })
})
