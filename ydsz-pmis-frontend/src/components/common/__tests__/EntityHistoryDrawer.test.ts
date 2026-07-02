import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EntityHistoryDrawer from '../EntityHistoryDrawer.vue'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: { records: [] } })
  }
}))

describe('EntityHistoryDrawer', () => {
  it('should render drawer when visible', () => {
    const wrapper = mount(EntityHistoryDrawer, {
      props: { visible: true, entityType: 'contract', entityId: 1 }
    })
    expect(wrapper.findComponent({ name: 'ElDrawer' }).exists()).toBe(true)
  })

  it('should get correct change type color', () => {
    const wrapper = mount(EntityHistoryDrawer, {
      props: { visible: false, entityType: 'contract', entityId: 1 }
    })
    expect((wrapper.vm as any).getChangeTypeColor('ADD')).toBe('success')
    expect((wrapper.vm as any).getChangeTypeColor('DELETE')).toBe('danger')
    expect((wrapper.vm as any).getChangeTypeColor('MODIFY')).toBe('warning')
  })

  it('should get correct change type label', () => {
    const wrapper = mount(EntityHistoryDrawer, {
      props: { visible: false, entityType: 'contract', entityId: 1 }
    })
    expect((wrapper.vm as any).getChangeTypeLabel('ADD')).toBe('新增')
    expect((wrapper.vm as any).getChangeTypeLabel('DELETE')).toBe('删除')
    expect((wrapper.vm as any).getChangeTypeLabel('MODIFY')).toBe('修改')
  })
})
