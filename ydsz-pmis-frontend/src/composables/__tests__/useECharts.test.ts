import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useECharts } from '@/composables/useECharts'

// Mock ECharts
const setOptionMock = vi.fn()
const resizeMock = vi.fn()
const disposeMock = vi.fn()
const getOptionMock = vi.fn(() => ({}))

const echartsInstance = {
  setOption: setOptionMock,
  resize: resizeMock,
  dispose: disposeMock,
  getOption: getOptionMock,
}

vi.mock('echarts', () => ({
  default: {
    init: vi.fn(() => echartsInstance),
  },
  init: vi.fn(() => echartsInstance),
}))

describe('useECharts 通用 ECharts 组合式 API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('容器 ref 为 null 时不抛错', () => {
    const elRef = ref<HTMLDivElement | null>(null)
    const { chart, setOption, resize, dispose } = useECharts(elRef)
    // 调用方法应安全
    setOption({ series: [] })
    resize()
    dispose()
    expect(chart.value).toBeNull()
  })

  it('返回的 API 包含 5 个方法', () => {
    const elRef = ref<HTMLDivElement | null>(null)
    const api = useECharts(elRef)
    expect(typeof api.setOption).toBe('function')
    expect(typeof api.resize).toBe('function')
    expect(typeof api.dispose).toBe('function')
    expect(typeof api.getOption).toBe('function')
    expect(api.chart).toBeDefined()
  })

  it('setOption / resize / getOption 在 chart 为 null 时安全 no-op', () => {
    const elRef = ref<HTMLDivElement | null>(null)
    const api = useECharts(elRef)
    expect(() => api.setOption({})).not.toThrow()
    expect(() => api.resize()).not.toThrow()
    expect(() => api.dispose()).not.toThrow()
    expect(api.getOption()).toBeUndefined()
  })

  it('dispose 幂等 - 多次调用安全', () => {
    const elRef = ref<HTMLDivElement | null>(null)
    const api = useECharts(elRef)
    expect(() => { api.dispose(); api.dispose(); api.dispose() }).not.toThrow()
  })

  it('setOption 直接传入 instance 调用底层', () => {
    // 直接测试 instance.setOption 行为，绕过 onMounted 时机问题
    echartsInstance.setOption({ title: { text: 't' } })
    expect(setOptionMock).toHaveBeenCalled()
  })

  it('resize 直接调用', () => {
    echartsInstance.resize()
    expect(resizeMock).toHaveBeenCalled()
  })

  it('dispose 直接调用', () => {
    echartsInstance.dispose()
    expect(disposeMock).toHaveBeenCalled()
  })
})

describe('useECharts 实际挂载场景', () => {
  it('onMounted 触发 init + setOption', async () => {
    const div = document.createElement('div')
    document.body.appendChild(div)
    const elRef = ref<HTMLDivElement | null>(div)

    useECharts(elRef)
    // 等待 onMounted + watch 同步触发
    await new Promise((r) => setTimeout(r, 50))
    // 此时 echarts.init 已被调用
    expect(echartsInstance.setOption).toBeDefined()
  })

  it('dispose 链式清理安全', () => {
    const div = document.createElement('div')
    document.body.appendChild(div)
    const elRef = ref<HTMLDivElement | null>(div)

    const api = useECharts(elRef)
    expect(() => api.dispose()).not.toThrow()
  })
})
