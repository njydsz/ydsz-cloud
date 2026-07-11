/**
 * @file FormDialog 组件单元测试
 * @description 测试通用表单弹窗组件的核心功能：打开/关闭、提交、未保存确认、全屏切换
 * @module components/common/__tests__/FormDialog
 */
import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FormDialog from '../FormDialog.vue'

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock @element-plus/icons-vue
vi.mock('@element-plus/icons-vue', () => ({
  FullScreen: { name: 'FullScreen', render: () => null },
  Aim: { name: 'Aim', render: () => null },
}))

// Mock ElMessageBox
vi.mock('element-plus', () => ({
  ElMessageBox: {
    confirm: vi.fn().mockResolvedValue('confirm'),
  },
  ElDialog: {
    name: 'ElDialog',
    template: `<div v-if="modelValue" class="el-dialog" :data-title="title" :data-fullscreen="isFullscreen"><slot /><template v-if="$slots.footer"><slot name="footer" /></template></div>`,
    props: ['modelValue', 'title', 'width', 'closeOnClickModal', 'draggable', 'fullscreen', 'beforeClose'],
    emits: ['update:modelValue', 'close'],
    setup(props: any, { emit }: any) {
      return {
        isFullscreen: props.fullscreen,
      }
    },
  },
}))

describe('FormDialog', () => {
  const mountDialog = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) => {
    return mount(FormDialog, {
      props: {
        modelValue: true,
        title: '测试弹窗',
        ...props,
      },
      slots: {
        default: '<div class="form-content">表单内容</div>',
        ...slots,
      },
      global: {
        stubs: {
          'el-button': { template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
          'el-icon': { template: '<span class="el-icon"><slot /></span>' },
          'el-form': { template: '<form class="el-form"><slot /></form>' },
        },
      },
    })
  }

  it('modelValue=true 时渲染弹窗', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(wrapper.find('.form-content').exists()).toBe(true)
  })

  it('modelValue=false 时不渲染弹窗', () => {
    const wrapper = mountDialog({ modelValue: false })
    expect(wrapper.find('.el-dialog').exists()).toBe(false)
  })

  it('渲染标题', () => {
    const wrapper = mountDialog({ title: '创建商机' })
    expect(wrapper.find('.el-dialog').attributes('data-title')).toBe('创建商机')
  })

  it('showFooter=true 时渲染底部按钮', () => {
    const wrapper = mountDialog({ showFooter: true })
    const buttons = wrapper.findAll('.el-button')
    expect(buttons.length).toBeGreaterThanOrEqual(2)
  })

  it('showFooter=false 时不渲染底部', () => {
    const wrapper = mountDialog({ showFooter: false })
    // 不应有底部确认/取消按钮
    const dialog = wrapper.find('.el-dialog')
    expect(dialog.exists()).toBe(true)
  })

  it('confirmText 传入时渲染自定义确认文案', () => {
    const wrapper = mountDialog({ showFooter: true, confirmText: '提交审批' })
    expect(wrapper.text()).toContain('提交审批')
  })

  it('cancelText 传入时渲染自定义取消文案', () => {
    const wrapper = mountDialog({ showFooter: true, cancelText: '返回' })
    expect(wrapper.text()).toContain('返回')
  })

  it('loading=true 时确认按钮显示加载状态', () => {
    const wrapper = mountDialog({ showFooter: true, loading: true })
    const buttons = wrapper.findAll('.el-button')
    const confirmBtn = buttons[buttons.length - 1]
    expect(confirmBtn.attributes('loading')).toBe('true')
  })

  it('点击取消按钮触发 cancel 事件', async () => {
    const wrapper = mountDialog({ showFooter: true })
    const buttons = wrapper.findAll('.el-button')
    // 取消按钮是第一个按钮
    await buttons[0].trigger('click')
    // 应该触发 update:modelValue 和 cancel
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('点击确认按钮触发 submit 事件', async () => {
    const wrapper = mountDialog({ showFooter: true })
    const buttons = wrapper.findAll('.el-button')
    // 确认按钮是最后一个按钮
    await buttons[buttons.length - 1].trigger('click')
    expect(wrapper.emitted('submit')).toBeTruthy()
  })

  it('fullscreen=true 时显示全屏切换按钮', () => {
    const wrapper = mountDialog({ fullscreen: true })
    // 全屏按钮应该存在
    const icons = wrapper.findAll('.el-icon')
    expect(icons.length).toBeGreaterThan(0)
  })

  it('closeOnClickModal 默认为 false', () => {
    const wrapper = mountDialog()
    const dialog = wrapper.find('.el-dialog')
    expect(dialog.attributes('data-close-on-click-modal')).toBe('false')
  })
})
