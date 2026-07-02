import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EntityHistoryDrawer from '../EntityHistoryDrawer.vue'

// 组件依赖 @/api/audit 模块（而非直接使用 request），按新依赖打桩
vi.mock('@/api/audit', () => ({
  getOperationLogPage: vi.fn().mockResolvedValue({ data: { list: [], total: 0 } }),
  getOperationLogDiff: vi.fn().mockResolvedValue({ data: [] }),
}))

// 桩 Element Plus 组件：ElTableColumn 不渲染默认插槽，避免无数据时
// `#default="{ row }"` 因缺少作用域对象而触发解构报错
const elStubs = {
  ElDrawer: { name: 'ElDrawer', template: '<div><slot /></div>' },
  ElTable: { name: 'ElTable', template: '<div><slot /></div>' },
  ElTableColumn: { name: 'ElTableColumn', template: '<div />' },
  ElDialog: { name: 'ElDialog', template: '<div><slot /></div>' },
  ElButton: { name: 'ElButton', template: '<button><slot /></button>' },
  ElTag: { name: 'ElTag', template: '<span><slot /></span>' },
}

describe('EntityHistoryDrawer', () => {
  it('should render drawer when visible', () => {
    const wrapper = mount(EntityHistoryDrawer, {
      props: { visible: true, entityType: 'contract', entityId: 1 },
      global: { stubs: elStubs },
    })
    expect(wrapper.findComponent({ name: 'ElDrawer' }).exists()).toBe(true)
  })

  it('should get correct change type color', () => {
    const wrapper = mount(EntityHistoryDrawer, {
      props: { visible: false, entityType: 'contract', entityId: 1 },
      global: { stubs: elStubs },
    })
    expect((wrapper.vm as any).getChangeTypeColor('ADD')).toBe('success')
    expect((wrapper.vm as any).getChangeTypeColor('DELETE')).toBe('danger')
    expect((wrapper.vm as any).getChangeTypeColor('MODIFY')).toBe('warning')
  })

  it('should get correct change type label', () => {
    const wrapper = mount(EntityHistoryDrawer, {
      props: { visible: false, entityType: 'contract', entityId: 1 },
      global: { stubs: elStubs },
    })
    expect((wrapper.vm as any).getChangeTypeLabel('ADD')).toBe('新增')
    expect((wrapper.vm as any).getChangeTypeLabel('DELETE')).toBe('删除')
    expect((wrapper.vm as any).getChangeTypeLabel('MODIFY')).toBe('修改')
  })
})
