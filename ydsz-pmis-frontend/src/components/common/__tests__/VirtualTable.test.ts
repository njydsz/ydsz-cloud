/**
 * @file VirtualTable 虚拟滚动表格组件 单元测试（P1-9）
 * @description 覆盖表格渲染、虚拟滚动配置开启、复选框列配置、选择事件派发等场景.
 * @module components/common/__tests__/VirtualTable
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import VirtualTable from '../VirtualTable.vue'

// Mock vxe-table，避免在 jsdom 中加载真实表格组件
vi.mock('vxe-table', () => ({
  VxeTable: { template: '<table class="vxe-table-mock"><slot /></table>' },
  VxeColumn: { template: '<td class="vxe-col-mock"><slot /></td>' },
}))

describe('VirtualTable', () => {
  const mockColumns = [
    { field: 'name', title: '名称', width: 200 },
    { field: 'status', title: '状态', width: 100 },
  ]

  const mockData = Array.from({ length: 100 }, (_, i) => ({
    id: i + 1,
    name: `项目 ${i + 1}`,
    status: '进行中',
  }))

  it('should render table with data', () => {
    const wrapper = mount(VirtualTable, {
      props: { data: mockData, columns: mockColumns },
    })
    expect(wrapper.find('.vxe-table-mock').exists()).toBe(true)
  })

  it('should enable virtual scroll when data > 50', () => {
    const wrapper = mount(VirtualTable, {
      props: { data: mockData, columns: mockColumns },
    })
    expect(wrapper.vm.tableConfig.scrollY?.enabled).toBe(true)
    expect(wrapper.vm.tableConfig.scrollY?.gt).toBe(50)
  })

  it('should show checkbox column when enabled', () => {
    const wrapper = mount(VirtualTable, {
      props: { data: mockData, columns: mockColumns, checkbox: true },
    })
    expect(wrapper.vm.tableConfig.checkboxConfig).toBeDefined()
  })

  it('should emit selection-change when checkbox toggled', async () => {
    const wrapper = mount(VirtualTable, {
      props: { data: mockData, columns: mockColumns, checkbox: true },
    })
    wrapper.vm.handleSelectionChange({ records: mockData.slice(0, 5) })
    expect(wrapper.emitted('selection-change')).toBeTruthy()
    // emitted('selection-change')[0] 是首次派发的参数数组，[0][0] 即传入的 records
    expect(wrapper.emitted('selection-change')![0][0]).toEqual(mockData.slice(0, 5))
  })
})
