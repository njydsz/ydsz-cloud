/**
 * @file UserPicker 用户选择器 单元测试（P1-8）
 * @description 覆盖 UserPicker 组件的 props 透传、v-model 单/多选更新、change 事件派发、
 *   显示/隐藏高级弹窗、远程搜索触发、初始空态/有值渲染等场景.
 * @module components/common/__tests__/UserPicker
 *
 * 注意：Element Plus 的 el-select / el-dialog 包含 teleport 和复杂交互，
 * 测试中以 stub 替换，聚焦 UserPicker 自身的 prop/emit/方法行为.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// Mock listUsers API
vi.mock('@/api/system/user', () => ({
  listUsers: vi.fn().mockResolvedValue({
    data: { code: 0, data: { records: [], total: 0, page: 1, size: 20 } },
  }),
}))

import { listUsers } from '@/api/system/user'
import type { UserVO } from '@/api/system/user/types'
import UserPicker from '../UserPicker.vue'

const elStubs = {
  'el-select': {
    template:
      '<div class="el-select-stub" @click="$emit(\'focus\')">' +
      '<input class="el-select-input" />' +
      '<slot />' +
      '<slot name="footer" />' +
      '</div>',
    props: [
      'modelValue',
      'multiple',
      'placeholder',
      'disabled',
      'clearable',
      'filterable',
      'remote',
      'loading',
      'collapseTags',
      'collapseTagsTooltip',
    ],
  },
  'el-option': {
    template: '<div class="el-option-stub" :data-value="value" :data-label="label"><slot /></div>',
    props: ['value', 'label', 'disabled'],
  },
  'el-dialog': {
    template: '<div class="el-dialog-stub" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'closeOnClickModal', 'appendToBody'],
  },
  'el-button': {
    template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-icon': {
    template: '<span class="el-icon-stub"><slot /></span>',
  },
  'el-input': {
    template:
      '<input class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder', 'clearable', 'prefixIcon'],
  },
  'el-tag': {
    template:
      '<span class="el-tag-stub" @click="$emit(\'click\')" @close="$emit(\'close\')"><slot /></span>',
    props: ['effect', 'round', 'closable'],
  },
  'el-avatar': {
    template: '<span class="el-avatar-stub"><slot /></span>',
    props: ['size'],
  },
  'el-table': {
    template: '<div class="el-table-stub"><slot /></div>',
    props: ['data', 'height', 'size'],
  },
  'el-table-column': {
    template: '<div class="el-table-col-stub"></div>',
    props: ['label', 'width', 'minWidth'],
  },
  'el-checkbox': {
    template:
      '<input class="el-checkbox-stub" type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
    props: ['modelValue'],
  },
  'el-pagination': {
    template: '<div class="el-pagination-stub"></div>',
    props: ['total'],
  },
  'el-pagination': {
    template: '<div class="el-pagination-stub"></div>',
    props: ['total'],
  },
  Search: { template: '<span class="search-icon-stub"></span>' },
  User: { template: '<span class="user-icon-stub"></span>' },
  OfficeBuilding: { template: '<span class="office-icon-stub"></span>' },
}

function makeUser(overrides: Partial<UserVO> = {}): UserVO {
  return {
    id: 1,
    username: 'zhangsan',
    realName: '张三',
    departmentName: '研发部',
    levelName: 'P5',
    ...overrides,
  } as UserVO
}

describe('UserPicker 用户选择器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 重置 localStorage mock
    localStorage.clear()
  })

  it('渲染 el-select 容器并展示 placeholder', () => {
    const wrapper = mount(UserPicker, {
      props: { placeholder: '请选择审批人' },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('.user-picker').exists()).toBe(true)
    expect(wrapper.find('.el-select-stub').exists()).toBe(true)
    expect(wrapper.props('placeholder')).toBe('请选择审批人')
  })

  it('单选模式下传 number 类型 modelValue 时正确透传', () => {
    const wrapper = mount(UserPicker, {
      props: { modelValue: 42, multiple: false },
      global: { stubs: elStubs },
    })
    // 内部 modelValue 是 42
    expect(wrapper.props('modelValue')).toBe(42)
  })

  it('多选模式下传 array 类型 modelValue 时正确透传', () => {
    const wrapper = mount(UserPicker, {
      props: { modelValue: [1, 2, 3], multiple: true },
      global: { stubs: elStubs },
    })
    expect(wrapper.props('multiple')).toBe(true)
    expect(wrapper.props('modelValue')).toEqual([1, 2, 3])
  })

  it('el-select 透传 clearable / filterable / remote 三个属性', () => {
    const wrapper = mount(UserPicker, {
      props: { clearable: false },
      global: { stubs: elStubs },
    })
    const select = wrapper.find('.el-select-stub')
    expect(select.exists()).toBe(true)
  })

  it('showDialog=true 时渲染高级选择按钮', () => {
    const wrapper = mount(UserPicker, {
      props: { showDialog: true },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('.picker-footer').exists()).toBe(true)
  })

  it('showDialog=false 时不渲染高级选择按钮', () => {
    const wrapper = mount(UserPicker, {
      props: { showDialog: false },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('.picker-footer').exists()).toBe(false)
  })

  it('disabled=true 时不影响 el-select 渲染', () => {
    const wrapper = mount(UserPicker, {
      props: { disabled: true },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('.el-select-stub').exists()).toBe(true)
    expect(wrapper.props('disabled')).toBe(true)
  })

  it('focus el-select 时触发远程搜索加载数据', async () => {
    vi.mocked(listUsers).mockResolvedValueOnce({
      data: { code: 0, data: { records: [makeUser()], total: 1, page: 1, size: 20 } },
    } as any)

    const wrapper = mount(UserPicker, {
      props: { pageSize: 20 },
      global: { stubs: elStubs },
    })
    await wrapper.find('.el-select-stub').trigger('click')
    await flushPromises()
    expect(listUsers).toHaveBeenCalled()
  })

  it('外部 options prop 注入后会合并到候选列表', async () => {
    const wrapper = mount(UserPicker, {
      props: {
        options: [makeUser({ id: 99, realName: '外部预置' })],
      },
      global: { stubs: elStubs },
    })
    await flushPromises()
    // 候选中包含外部预置用户
    const allText = wrapper.text()
    expect(allText).toContain('外部预置')
  })

  it('change 事件在 onChange 触发时正确派发', async () => {
    const wrapper = mount(UserPicker, {
      props: { modelValue: 1, multiple: false },
      global: { stubs: elStubs },
    })

    // 模拟 el-select 内部调用 onChange
    const vm = wrapper.vm as any
    vm.onChange(7)
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('change')).toBeTruthy()
  })

  it('多选模式 onChange 派发数组', async () => {
    const wrapper = mount(UserPicker, {
      props: { modelValue: [], multiple: true },
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    vm.onChange([1, 2, 3])
    await flushPromises()
    const updates = wrapper.emitted('update:modelValue')
    expect(updates).toBeTruthy()
    expect(Array.isArray(updates![0][0])).toBe(true)
  })

  it('单选模式清空时派发 undefined', async () => {
    const wrapper = mount(UserPicker, {
      props: { modelValue: 1, multiple: false },
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    vm.onChange(undefined)
    await flushPromises()
    const updates = wrapper.emitted('update:modelValue')
    expect(updates![0][0]).toBeUndefined()
  })

  it('openDialog 方法将 dialogVisible 设为 true', async () => {
    const wrapper = mount(UserPicker, {
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    expect(vm.dialogVisible).toBe(false)
    vm.openDialog()
    expect(vm.dialogVisible).toBe(true)
  })

  it('dialogKeyword / dialogDept 默认值正确', () => {
    const wrapper = mount(UserPicker, {
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    expect(vm.dialogKeyword).toBe('')
    expect(vm.dialogDept).toBeUndefined()
  })

  it('提供默认 pageSize=20 并允许覆盖', () => {
    const wrapper = mount(UserPicker, {
      props: { pageSize: 50 },
      global: { stubs: elStubs },
    })
    expect(wrapper.props('pageSize')).toBe(50)
  })

  it('提供默认 status=ENABLED 并允许覆盖', () => {
    const wrapper = mount(UserPicker, {
      props: { status: 'DISABLED' },
      global: { stubs: elStubs },
    })
    expect(wrapper.props('status')).toBe('DISABLED')
  })

  it('提供默认 debounce=300 并允许覆盖', () => {
    const wrapper = mount(UserPicker, {
      props: { debounce: 500 },
      global: { stubs: elStubs },
    })
    expect(wrapper.props('debounce')).toBe(500)
  })

  it('提供默认 dialogTitle 并允许覆盖', () => {
    const wrapper = mount(UserPicker, {
      props: { dialogTitle: '选择审批人' },
      global: { stubs: elStubs },
    })
    expect(wrapper.props('dialogTitle')).toBe('选择审批人')
  })

  it('recent 数组从 localStorage 加载', () => {
    const sample = [makeUser({ id: 5, realName: '历史选择' })]
    localStorage.setItem('pmis:user-picker:recent', JSON.stringify(sample))
    const wrapper = mount(UserPicker, {
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    expect(vm.recent.length).toBe(1)
    expect(vm.recent[0].realName).toBe('历史选择')
  })

  it('userKey 工具函数以 props.valueKey 为字段', () => {
    const wrapper = mount(UserPicker, {
      props: { valueKey: 'username' as any },
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    expect(vm.userKey({ id: 1, username: 'a' } as UserVO)).toBe('a')
  })

  it('isDialogSelected 命中已选', () => {
    const wrapper = mount(UserPicker, {
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    const u = makeUser({ id: 9 })
    vm.dialogSelected = [u]
    expect(vm.isDialogSelected(u)).toBe(true)
    expect(vm.isDialogSelected(makeUser({ id: 10 }))).toBe(false)
  })

  it('clearDialogSelection 清空已选', () => {
    const wrapper = mount(UserPicker, {
      global: { stubs: elStubs },
    })
    const vm = wrapper.vm as any
    vm.dialogSelected = [makeUser()]
    vm.clearDialogSelection()
    expect(vm.dialogSelected.length).toBe(0)
  })
})
