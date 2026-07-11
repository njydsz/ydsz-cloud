/**
 * @file ProTable 组件单元测试
 * @description 测试高级表格组件的核心功能：渲染、分页、选择、排序、列设置
 * @module components/common/__tests__/ProTable
 */
import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ProTable, { type ProTableColumn } from '../ProTable.vue'

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock @element-plus/icons-vue
vi.mock('@element-plus/icons-vue', () => ({
  Document: { name: 'Document', render: () => null },
  Setting: { name: 'Setting', render: () => null },
  Rank: { name: 'Rank', render: () => null },
  FullScreen: { name: 'FullScreen', render: () => null },
}))

interface TestRow extends Record<string, unknown> {
  id: string
  name: string
  status: string
  amount: number
}

const mockColumns: ProTableColumn<TestRow>[] = [
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'name', label: '名称', minWidth: 120 },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'amount', label: '金额', width: 120, align: 'right', sortable: true },
]

const mockData: TestRow[] = [
  { id: '1', name: '测试项目A', status: 'ACTIVE', amount: 10000 },
  { id: '2', name: '测试项目B', status: 'CLOSED', amount: 20000 },
  { id: '3', name: '测试项目C', status: 'ACTIVE', amount: 30000 },
]

describe('ProTable', () => {
  it('渲染表格列头', () => {
    const wrapper = mount(ProTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        loading: false,
        total: 3,
      },
      global: {
        stubs: {
          'el-table': {
            template: '<div class="el-table"><slot /><div v-for="c in columns" :key="c.prop" class="el-table__column-header">{{ c.label }}</div></div>',
            props: ['data', 'loading', 'border', 'maxHeight', 'height', 'rowKey', 'defaultSort', 'summaryMethod', 'showSummary', 'size'],
          },
          'el-table-column': { template: '<div class="el-table-column"><slot /></div>' },
          'el-pagination': { template: '<div class="el-pagination" />' },
          'el-button': { template: '<button class="el-button"><slot /></button>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div class="el-dropdown"><slot /></div>' },
          'el-icon': { template: '<span class="el-icon"><slot /></span>' },
        },
      },
    })

    expect(wrapper.text()).toContain('ID')
    expect(wrapper.text()).toContain('名称')
    expect(wrapper.text()).toContain('状态')
    expect(wrapper.text()).toContain('金额')
  })

  it('loading=true 时显示加载状态', () => {
    const wrapper = mount(ProTable, {
      props: {
        columns: mockColumns,
        data: [],
        loading: true,
        total: 0,
      },
      global: {
        stubs: {
          'el-table': { template: '<div class="el-table" :class="{ \'is-loading\': loading }"><slot /></div>', props: ['data', 'loading'] },
          'el-table-column': { template: '<div><slot /></div>' },
          'el-pagination': { template: '<div class="el-pagination" />' },
          'el-button': { template: '<button><slot /></button>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div><slot /></div>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })

    const table = wrapper.find('.el-table')
    expect(table.classes()).toContain('is-loading')
  })

  it('空数据时渲染空状态', () => {
    const wrapper = mount(ProTable, {
      props: {
        columns: mockColumns,
        data: [],
        loading: false,
        total: 0,
      },
      global: {
        stubs: {
          'el-table': { template: '<div class="el-table"><slot name="empty" /></div>' },
          'el-table-column': { template: '<div><slot /></div>' },
          'el-pagination': { template: '<div />' },
          'el-button': { template: '<button><slot /></button>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div><slot /></div>' },
          'el-icon': { template: '<span><slot /></span>' },
          EmptyState: { template: '<div class="empty-state">empty</div>' },
        },
      },
    })

    // EmptyState 应该被渲染
    expect(wrapper.find('.empty-state').exists()).toBe(true)
  })

  it('分页器存在且接收正确的 page/size', () => {
    const wrapper = mount(ProTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        loading: false,
        total: 100,
        page: 2,
        size: 20,
      },
      global: {
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': { template: '<div><slot /></div>' },
          'el-pagination': {
            template: '<div class="el-pagination" :data-current="currentPage" :data-size="pageSize" />',
            props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout'],
          },
          'el-button': { template: '<button><slot /></button>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div><slot /></div>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })

    const pagination = wrapper.find('.el-pagination')
    expect(pagination.exists()).toBe(true)
    expect(pagination.attributes('data-current')).toBe('2')
    expect(pagination.attributes('data-size')).toBe('20')
  })

  it('selection=true 时渲染多选列', () => {
    const wrapper = mount(ProTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        loading: false,
        total: 3,
        selection: true,
      },
      global: {
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': { template: '<div class="el-table-column" :data-type="type"><slot /></div>', props: ['type'] },
          'el-pagination': { template: '<div />' },
          'el-button': { template: '<button><slot /></button>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div><slot /></div>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })

    // 多选列应该有 type="selection"
    const columns = wrapper.findAll('.el-table-column')
    const selectionCol = columns.find(c => c.attributes('data-type') === 'selection')
    expect(selectionCol).toBeDefined()
  })

  it('toolbar=true 时渲染工具栏', () => {
    const wrapper = mount(ProTable, {
      props: {
        columns: mockColumns,
        data: mockData,
        loading: false,
        total: 3,
        toolbar: true,
      },
      slots: {
        toolbar: '<button class="custom-btn">自定义按钮</button>',
      },
      global: {
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': { template: '<div><slot /></div>' },
          'el-pagination': { template: '<div />' },
          'el-button': { template: '<button class="el-button"><slot /></button>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div class="el-dropdown"><slot /></div>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })

    expect(wrapper.find('.custom-btn').exists()).toBe(true)
  })
})
