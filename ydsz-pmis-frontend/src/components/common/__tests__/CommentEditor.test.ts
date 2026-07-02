/**
 * @file CommentEditor 意见编辑器 单元测试（P1-9）
 * @description 覆盖 CommentEditor 的 v-model 双向绑定、常用语插入、@人提及、
 *   图片附件增删、字数统计、清空操作等场景.
 * @module components/common/__tests__/CommentEditor
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/system/user', () => ({
  listUsers: vi.fn().mockResolvedValue({
    data: { code: 0, data: { records: [], total: 0, page: 1, size: 20 } },
  }),
}))

import CommentEditor from '../CommentEditor.vue'

const elStubs = {
  'el-input': {
    template:
      '<textarea class="el-textarea-inner" :value="modelValue" :placeholder="placeholder" :disabled="disabled" :readonly="readonly" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'readonly', 'maxlength'],
    mounted(this: any) {
      this.focus = () => {}
      this.setSelectionRange = () => {}
    },
  },
  'el-button': {
    template: '<button class="el-button-stub" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    props: ['size', 'type', 'disabled', 'loading'],
  },
  'el-popover': {
    template:
      '<div class="el-popover-stub"><div class="reference-slot"><slot name="reference" /></div><div v-if="visible" class="popover-content"><slot /></div></div>',
    props: ['visible', 'placement', 'width', 'trigger'],
  },
  'el-icon': {
    template: '<span class="el-icon-stub"><slot /></span>',
  },
  'el-tag': {
    template: '<span class="el-tag-stub" @close="$emit(\'close\')"><slot /></span>',
    props: ['size', 'type', 'closable', 'effect'],
  },
  'el-dialog': {
    template: '<div class="el-dialog-stub" v-if="modelValue"><slot /></div>',
    props: ['modelValue', 'title', 'width'],
  },
  UserPicker: {
    template: '<div class="user-picker-stub" />',
    props: ['modelValue', 'placeholder', 'showDialog'],
  },
  ChatLineSquare: { template: '<span></span>' },
  Promotion: { template: '<span></span>' },
  Picture: { template: '<span></span>' },
  Delete: { template: '<span></span>' },
  View: { template: '<span></span>' },
}

function factory(props = {}, options: Record<string, unknown> = {}) {
  return mount(CommentEditor, {
    props: { modelValue: '', ...props },
    global: { stubs: elStubs },
    ...options,
  })
}

describe('CommentEditor 意见编辑器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('渲染 textarea 与工具栏', () => {
    const wrapper = factory()
    expect(wrapper.find('.comment-editor').exists()).toBe(true)
    expect(wrapper.find('textarea.el-textarea-inner').exists()).toBe(true)
  })

  it('v-model 双向绑定：输入触发 update:modelValue', async () => {
    const wrapper = factory({ modelValue: '' })
    const ta = wrapper.find('textarea.el-textarea-inner')
    await ta.setValue('同意')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('同意')
  })

  it('字符数统计：maxlength=100 时显示 0/100 初始', () => {
    const wrapper = factory({ maxlength: 100, modelValue: '' })
    expect(wrapper.find('.char-counter').text()).toContain('0 / 100')
  })

  it('字符数统计：动态计数', () => {
    const wrapper = factory({ maxlength: 100, modelValue: '同意，请按计划推进' })
    expect(wrapper.find('.char-counter').text()).toContain('9 / 100')
  })

  it('maxlength=0 不显示字数统计', () => {
    const wrapper = factory({ maxlength: 0, modelValue: '' })
    expect(wrapper.find('.char-counter').exists()).toBe(false)
  })

  it('enablePhrases=false 隐藏常用语按钮', () => {
    const wrapper = factory({ enablePhrases: false })
    const popovers = wrapper.findAll('.el-popover-stub')
    // 应只显示 @ 人 popover
    expect(popovers.length).toBe(1)
  })

  it('enableMention=false 隐藏 @人 按钮', () => {
    const wrapper = factory({ enableMention: false })
    const popovers = wrapper.findAll('.el-popover-stub')
    expect(popovers.length).toBe(1) // 仅常用语
  })

  it('enableImage=false 隐藏图片按钮', () => {
    const wrapper = factory({ enableImage: false })
    expect(wrapper.find('.upload-trigger').exists()).toBe(false)
  })

  it('readonly 模式隐藏工具栏与清空按钮', () => {
    const wrapper = factory({ readonly: true, modelValue: 'test' })
    expect(wrapper.find('.comment-toolbar').exists()).toBe(false)
  })

  it('disabled 模式禁用 el-input', () => {
    const wrapper = factory({ disabled: true })
    const ta = wrapper.find('textarea.el-textarea-inner')
    expect(ta.attributes('disabled')).toBeDefined()
  })

  it('placeholder 透传', () => {
    const wrapper = factory({ placeholder: '请输入意见' })
    const ta = wrapper.find('textarea.el-textarea-inner')
    expect(ta.attributes('placeholder')).toBe('请输入意见')
  })

  it('phrases prop 自定义常用语列表', () => {
    const wrapper = factory({ phrases: ['自定义1', '自定义2'] })
    expect(wrapper.props('phrases')).toEqual(['自定义1', '自定义2'])
  })

  it('insertAtCursor 暴露方法可用', async () => {
    const wrapper = factory({ modelValue: '' })
    const vm = wrapper.vm as any
    vm.insertAtCursor('同意')
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('同意')
  })

  it('attachments v-model 双向同步', async () => {
    const wrapper = factory({
      attachments: [{ uid: 'a1', name: 'a.png', url: 'http://x' }],
    })
    await flushPromises()
    expect(wrapper.find('.attachment-bar').exists()).toBe(true)
  })

  it('attachments 列表中渲染文件名与大小', async () => {
    const wrapper = factory({
      attachments: [
        { uid: 'a1', name: 'sample.png', url: 'http://x', size: 1024 * 5, type: 'image/png' },
      ],
    })
    await flushPromises()
    expect(wrapper.text()).toContain('sample.png')
    expect(wrapper.text()).toContain('5.0 KB')
  })

  it('mentions v-model 双向同步并显示已 @ 列表', async () => {
    const wrapper = factory({
      mentions: [{ userId: 1, name: '张三' }],
    })
    await flushPromises()
    expect(wrapper.find('.mention-bar').exists()).toBe(true)
    expect(wrapper.text()).toContain('@张三')
  })

  it('removeMention 移除指定 @', async () => {
    const wrapper = factory({
      mentions: [
        { userId: 1, name: '张三' },
        { userId: 2, name: '李四' },
      ],
      modelValue: '@张三 @李四 ',
    })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.removeMention({ userId: 1, name: '张三' })
    await flushPromises()
    const emit = wrapper.emitted('update:mentions')
    expect(emit).toBeTruthy()
    expect(emit![0][0].length).toBe(1)
    expect(emit![0][0][0].name).toBe('李四')
  })

  it('removeAttachment 移除附件', async () => {
    const wrapper = factory({
      attachments: [{ uid: 'a1', name: 'x.png', url: 'http://x' }],
    })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.removeAttachment({ uid: 'a1', name: 'x.png', url: 'http://x' })
    await flushPromises()
    const emit = wrapper.emitted('update:attachments')
    expect(emit).toBeTruthy()
    expect(emit![0][0].length).toBe(0)
  })

  it('onMentionPick 添加提及并插入文本', async () => {
    const wrapper = factory({ modelValue: '请' })
    const vm = wrapper.vm as any
    vm.onMentionPick({ id: 99, realName: '王五' })
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('请@王五 ')
    expect(wrapper.emitted('mention')).toBeTruthy()
  })

  it('onMentionPick 同名用户不重复添加', async () => {
    const wrapper = factory({
      mentions: [{ userId: 99, name: '王五' }],
    })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.onMentionPick({ id: 99, realName: '王五' })
    await flushPromises()
    // mentions 数量仍为 1
    const internalMentions = vm.internalMentions
    expect(internalMentions.length).toBe(1)
  })

  it('phrase 列表默认 7 条', () => {
    const wrapper = factory()
    expect(wrapper.props('phrases').length).toBe(7)
  })

  it('default maxSize=10', () => {
    const wrapper = factory()
    expect(wrapper.props('maxSize')).toBe(10)
  })

  it('default accept 包含 image/png 等', () => {
    const wrapper = factory()
    const accept = wrapper.props('accept')
    expect(accept).toContain('image/png')
    expect(accept).toContain('image/jpeg')
  })

  it('expose.focus 方法存在', () => {
    const wrapper = factory()
    const exposed = wrapper.vm.$.exposed as { focus?: () => void }
    expect(typeof exposed.focus).toBe('function')
  })

  it('expose.clearAll 方法存在', () => {
    const wrapper = factory()
    const exposed = wrapper.vm.$.exposed as { clearAll?: () => void }
    expect(typeof exposed.clearAll).toBe('function')
  })

  it('customUpload prop 可注入自定义上传逻辑', () => {
    const fn = vi.fn()
    const wrapper = factory({ customUpload: fn })
    expect(wrapper.props('customUpload')).toBe(fn)
  })

  it('phrasePick 时 phrasePopover 关闭', async () => {
    const wrapper = factory()
    const vm = wrapper.vm as any
    vm.phrasePopover = true
    vm.pickPhrase('同意')
    expect(vm.phrasePopover).toBe(false)
  })
})
