/**
 * @file EmbeddedApprovalPanel 嵌入式审批面板 单元测试（P2-2）
 * @description 覆盖面板的加载/未发起/审批人/发起人/历史轨迹/快捷操作等场景.
 * @module components/common/__tests__/EmbeddedApprovalPanel
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/workflow', () => ({
  loadEmbeddedPanel: vi.fn(),
  embeddedQuickAction: vi.fn().mockResolvedValue({ data: { code: 0, data: null } }),
  recommendApprovers: vi.fn().mockResolvedValue({ data: { code: 0, data: [] } }),
  draftComment: vi.fn().mockResolvedValue({
    data: { code: 0, data: { primary: '同意', alternatives: ['好的'] } },
  }),
}))

import EmbeddedApprovalPanel from '../EmbeddedApprovalPanel.vue'
import { loadEmbeddedPanel } from '@/api/workflow'

const elStubs = {
  'el-skeleton': { template: '<div class="el-skeleton-stub" />', props: ['rows', 'animated'] },
  'el-empty': {
    template: '<div class="el-empty-stub"><slot name="default" /></div>',
    props: ['description', 'imageSize'],
  },
  'el-button': {
    template:
      '<button class="el-button-stub" :disabled="disabled" :loading="loading" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size', 'icon', 'loading', 'text', 'disabled', 'circle'],
  },
  'el-tag': {
    template: '<span class="el-tag-stub"><slot /></span>',
    props: ['type', 'size', 'effect'],
  },
  'el-alert': {
    template: '<div class="el-alert-stub" :class="`type-${type}`"><slot /></div>',
    props: ['title', 'type', 'closable', 'showIcon'],
  },
  'el-tooltip': {
    template: '<div class="el-tooltip-stub"><slot /></div>',
    props: ['content', 'placement'],
  },
  'el-icon': { template: '<span class="el-icon-stub"><slot /></span>' },
  'el-divider': { template: '<div class="el-divider-stub"><slot /></div>' },
  'el-timeline': { template: '<div class="el-timeline-stub"><slot /></div>' },
  'el-timeline-item': {
    template: '<div class="el-timeline-item-stub" :class="`type-${type}`"><slot /></div>',
    props: ['type', 'timestamp', 'placement'],
  },
  'el-dialog': {
    template: '<div class="el-dialog-stub" v-if="modelValue"><slot /></div>',
    props: ['modelValue', 'title', 'width'],
  },
  CommentEditor: {
    template:
      '<textarea class="comment-editor-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue'],
  },
  UserPicker: {
    template: '<div class="user-picker-stub" />',
    props: ['modelValue', 'placeholder', 'showDialog', 'options'],
  },
  Refresh: { template: '<span></span>' },
  Share: { template: '<span></span>' },
  Promotion: { template: '<span></span>' },
  Bell: { template: '<span></span>' },
  Avatar: { template: '<span></span>' },
  User: { template: '<span></span>' },
  Timer: { template: '<span></span>' },
  MagicStick: { template: '<span></span>' },
  EditPen: { template: '<span></span>' },
  Clock: { template: '<span></span>' },
  Check: { template: '<span></span>' },
  Close: { template: '<span></span>' },
  Switch: { template: '<span></span>' },
  Position: { template: '<span></span>' },
  RefreshLeft: { template: '<span></span>' },
  ChatLineSquare: { template: '<span></span>' },
}

function factory(props: Record<string, unknown> = {}) {
  return mount(EmbeddedApprovalPanel, {
    props: {
      businessType: 'PROJECT_INITIATION',
      businessId: '1',
      ...props,
    },
    global: {
      stubs: elStubs,
    },
  })
}

describe('EmbeddedApprovalPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('未发起流程：渲染空状态', async () => {
    vi.mocked(loadEmbeddedPanel).mockResolvedValueOnce({
      code: 0,
      data: {
        businessType: 'X',
        businessId: '1',
        instance: null,
        diagram: null,
        currentTasks: [],
        history: [],
        myRole: 'OBSERVER',
        actions: ['SUBMIT'],
        aiAvailable: false,
        canRecall: false,
        finished: false,
        message: '未发起流程',
      },
    } as never)
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  it('审批人视角：渲染待办与操作按钮', async () => {
    vi.mocked(loadEmbeddedPanel).mockResolvedValueOnce({
      code: 0,
      data: {
        businessType: 'X',
        businessId: '1',
        instance: {
          id: 1,
          flowCode: 'X',
          flowName: '项目立项',
          flowStatus: 'RUNNING',
          currentNodeName: '部门经理审批',
          initiatorName: '张三',
          startAt: '2026-07-01T10:00:00',
        },
        diagram: { currentNodeName: '部门经理审批' },
        currentTasks: [
          {
            taskId: 10,
            nodeCode: 'manager',
            nodeName: '部门经理审批',
            assigneeType: 'USER',
            assigneeId: '100',
            assigneeName: '李四',
            taskStatus: 'PENDING',
            mine: true,
          },
        ],
        history: [
          {
            type: 'TASK',
            nodeName: '提交申请',
            assigneeName: '张三',
            action: 'PASS',
            comment: '请审批',
            timestamp: '2026-07-01T09:00:00',
          },
        ],
        myRole: 'APPROVER',
        actions: ['PASS', 'REJECT', 'TRANSFER', 'URGE'],
        aiAvailable: true,
        canRecall: false,
        finished: false,
        message: '流程进行中',
      },
    } as never)
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.task-card').exists()).toBe(true)
    expect(wrapper.find('.task-card.is-mine').exists()).toBe(true)
    expect(wrapper.findAll('.el-button-stub').length).toBeGreaterThan(0)
  })

  it('发起人视角：myRole 显示为 INITIATOR', async () => {
    vi.mocked(loadEmbeddedPanel).mockResolvedValueOnce({
      code: 0,
      data: {
        businessType: 'X',
        businessId: '1',
        instance: { id: 1, flowCode: 'X', flowName: 'X', flowStatus: 'RUNNING' },
        diagram: {},
        currentTasks: [],
        history: [],
        myRole: 'INITIATOR',
        actions: ['URGE', 'WITHDRAW'],
        aiAvailable: false,
        canRecall: true,
        finished: false,
        message: '流程进行中',
      },
    } as never)
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.my-role.role-initiator').exists()).toBe(true)
  })

  it('流程已结束：finished=true，message 提示', async () => {
    vi.mocked(loadEmbeddedPanel).mockResolvedValueOnce({
      code: 0,
      data: {
        businessType: 'X',
        businessId: '1',
        instance: { id: 1, flowCode: 'X', flowName: 'X', flowStatus: 'COMPLETED' },
        diagram: {},
        currentTasks: [],
        history: [],
        myRole: 'OBSERVER',
        actions: [],
        aiAvailable: false,
        canRecall: false,
        finished: true,
        message: '流程已结束',
      },
    } as never)
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.el-tag-stub').exists()).toBe(true)
  })

  it('业务 ID 变化触发重新加载', async () => {
    vi.mocked(loadEmbeddedPanel).mockResolvedValue({
      code: 0,
      data: {
        businessType: 'X',
        businessId: '1',
        instance: null,
        diagram: null,
        currentTasks: [],
        history: [],
        myRole: 'OBSERVER',
        actions: ['SUBMIT'],
        aiAvailable: false,
        canRecall: false,
        finished: false,
        message: '未发起流程',
      },
    } as never)
    const wrapper = factory({ businessId: '1' })
    await flushPromises()
    const initialCalls = vi.mocked(loadEmbeddedPanel).mock.calls.length
    await wrapper.setProps({ businessId: '2' })
    await flushPromises()
    expect(vi.mocked(loadEmbeddedPanel).mock.calls.length).toBeGreaterThan(initialCalls)
  })

  it('loadPanel 通过 defineExpose 暴露', async () => {
    vi.mocked(loadEmbeddedPanel).mockResolvedValue({
      code: 0,
      data: {
        businessType: 'X',
        businessId: '1',
        instance: null,
        diagram: null,
        currentTasks: [],
        history: [],
        myRole: 'OBSERVER',
        actions: ['SUBMIT'],
        aiAvailable: false,
        canRecall: false,
        finished: false,
        message: '未发起流程',
      },
    } as never)
    const wrapper = factory()
    await flushPromises()
    const exposed = wrapper.vm as unknown as { loadPanel: () => Promise<void> }
    expect(typeof exposed.loadPanel).toBe('function')
  })
})
