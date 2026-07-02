/**
 * @file FlowDiagramReplay 流程回放组件 单元测试
 * @description 覆盖 P2-4 流程图回放组件：步骤加载、播放控制、上一步/下一步、速度切换
 * @module views/workflow/components/__tests__/FlowDiagramReplay
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import FlowDiagramReplay from '../FlowDiagramReplay.vue'
import { elComponents } from '@/tests/setup'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'

// Mock 整个 @/api/workflow 模块，避免真实网络请求
vi.mock('@/api/workflow', () => {
  return {
    getDiagram: vi.fn().mockResolvedValue({
      data: {
        code: 0,
        data: {
          instanceId: 1,
          flowStatus: 'RUNNING',
          currentNodeCode: 'N1',
          nodes: [
            { nodeCode: 'START', nodeName: '开始', nodeType: 0, x: 0, y: 0 },
            { nodeCode: 'N1', nodeName: '审批 1', nodeType: 1, x: 100, y: 0 },
            { nodeCode: 'END', nodeName: '结束', nodeType: 6, x: 200, y: 0 },
          ],
          skips: [],
          activeNodeCodes: [],
          completedNodeCodes: [],
        },
      },
    }),
    getReplaySteps: vi.fn().mockResolvedValue({
      data: {
        code: 0,
        data: [
          {
            stepIndex: 0,
            type: 'START',
            timestamp: '2025-01-01T00:00:00',
            action: 'START',
            nodeState: 'ENTERED',
          },
          {
            stepIndex: 1,
            type: 'HIS_TASK',
            timestamp: '2025-01-01T00:01:00',
            nodeCode: 'N1',
            nodeName: '审批 1',
            actor: 1,
            actorName: '张三',
            action: 'PASSED',
            comment: '同意',
            nodeState: 'PASSED',
            durationMs: 1000,
          },
          {
            stepIndex: 2,
            type: 'END',
            timestamp: '2025-01-01T00:02:00',
            action: 'COMPLETED',
            nodeState: 'FINISHED',
          },
        ],
      },
    }),
  }
})

// Stub 内部 FlowDiagramViewer 以避免它内部组件未注册导致警告
vi.mock('../FlowDiagramViewer.vue', () => ({
  default: {
    name: 'FlowDiagramViewer',
    props: ['diagram'],
    emits: ['node-click'],
    template: '<div class="flow-diagram-viewer-stub" />',
  },
}))

import {
  ElTimeline,
  ElTimelineItem,
  ElDescriptions,
  ElDescriptionsItem,
  ElSlider,
  ElButtonGroup,
  ElEmpty,
} from 'element-plus'

const extraComponents = {
  ElTimeline,
  ElTimelineItem,
  ElDescriptions,
  ElDescriptionsItem,
  ElSlider,
  ElButtonGroup,
  ElEmpty,
}

function makeI18n() {
  return createI18n({
    legacy: false,
    globalInjection: true,
    locale: 'zh-CN',
    fallbackLocale: 'zh-CN',
    messages: {
      'zh-CN': zhCN,
      'en-US': enUS,
    },
  })
}

describe('FlowDiagramReplay 流程回放组件', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
  })

  it('挂载后自动加载数据', async () => {
    const wrapper = mount(FlowDiagramReplay, {
      props: { instanceId: 1 },
      global: {
        plugins: [makeI18n()],
        components: { ...elComponents, ...extraComponents },
      },
    })
    await flushPromises()
    // 加载完成后会显示 controls 与 steps
    expect(wrapper.find('.flow-replay__controls').exists()).toBe(true)
    expect(wrapper.findAll('.el-timeline-item').length).toBe(3)
  })

  it('上一步按钮 - currentIndex 不能小于 0', async () => {
    const wrapper = mount(FlowDiagramReplay, {
      props: { instanceId: 1 },
      global: {
        plugins: [makeI18n()],
        components: { ...elComponents, ...extraComponents },
      },
    })
    await flushPromises()
    // 找到 prev 按钮（label 含 prev）
    const prevBtn = wrapper.findAll('button').find((b) => b.text().includes('上一步'))
    expect(prevBtn).toBeTruthy()
    await prevBtn!.trigger('click')
    // 已经是 0，再点 prev 仍然为 0（不应负数）
    expect((wrapper.vm as unknown as { currentIndex: number }).currentIndex).toBe(0)
  })

  it('下一步按钮 - currentIndex 递增', async () => {
    const wrapper = mount(FlowDiagramReplay, {
      props: { instanceId: 1 },
      global: {
        plugins: [makeI18n()],
        components: { ...elComponents, ...extraComponents },
      },
    })
    await flushPromises()
    const nextBtn = wrapper.findAll('button').find((b) => b.text().includes('下一步'))
    expect(nextBtn).toBeTruthy()
    await nextBtn!.trigger('click')
    expect((wrapper.vm as unknown as { currentIndex: number }).currentIndex).toBe(1)
  })

  it('播放按钮 - 启动 setInterval 定时器', async () => {
    const wrapper = mount(FlowDiagramReplay, {
      props: { instanceId: 1 },
      global: {
        plugins: [makeI18n()],
        components: { ...elComponents, ...extraComponents },
      },
    })
    await flushPromises()
    // 找到 play 按钮（label 含 播放）
    const playBtn = wrapper.findAll('button').find((b) => b.text().includes('播放'))
    expect(playBtn).toBeTruthy()
    await playBtn!.trigger('click')
    // 推进 1 个 tick
    vi.advanceTimersByTime(1100)
    await flushPromises()
    // currentIndex 应该大于 0
    const idx = (wrapper.vm as unknown as { currentIndex: number }).currentIndex
    expect(idx).toBeGreaterThanOrEqual(1)
  })

  it('点击步骤列表 - 跳转到对应步骤', async () => {
    const wrapper = mount(FlowDiagramReplay, {
      props: { instanceId: 1 },
      global: {
        plugins: [makeI18n()],
        components: { ...elComponents, ...extraComponents },
      },
    })
    await flushPromises()
    const items = wrapper.findAll('.el-timeline-item')
    expect(items.length).toBe(3)
    // 点击第 3 步
    await items[2].trigger('click')
    expect((wrapper.vm as unknown as { currentIndex: number }).currentIndex).toBe(2)
  })
})

