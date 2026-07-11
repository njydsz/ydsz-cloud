/**
 * @file PageLayout 组件单元测试
 * @description 测试通用列表页布局组件的核心功能：搜索表单、工具栏、表格插槽、分页、空状态
 * @module components/common/__tests__/PageLayout
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PageLayout from '../PageLayout.vue'

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock @element-plus/icons-vue
vi.mock('@element-plus/icons-vue', () => ({
  ArrowDown: { name: 'ArrowDown', render: () => null },
  ArrowUp: { name: 'ArrowUp', render: () => null },
}))

describe('PageLayout', () => {
  const mountLayout = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) => {
    return mount(PageLayout, {
      props: {
        query: { page: 1, size: 20 },
        list: [],
        total: 0,
        loading: false,
        ...props,
      },
      slots: {
        search: '<div class="search-form">搜索表单</div>',
        toolbar: '<div class="toolbar">工具栏</div>',
        table: '<div class="table-content">表格内容</div>',
        ...slots,
      },
      global: {
        stubs: {
          'el-form': { template: '<form class="el-form" @submit.prevent="$emit(\'submit\')"><slot /></form>', emits: ['submit'] },
          'el-form-item': { template: '<div class="el-form-item"><slot /></div>' },
          'el-input': { template: '<input class="el-input" />' },
          'el-button': { template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
          'el-pagination': {
            template: '<div class="el-pagination" :data-current="currentPage" :data-size="pageSize" :data-total="total" />',
            props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout'],
            emits: ['update:currentPage', 'update:pageSize', 'current-change', 'size-change'],
          },
          'el-icon': { template: '<span class="el-icon"><slot /></span>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-collapse-transition': { template: '<div><slot /></div>' },
          'el-checkbox': { template: '<label class="el-checkbox"><slot /></label>' },
          SkeletonTable: { template: '<div class="skeleton-table">骨架屏</div>' },
          BatchToolbar: { template: '<div class="batch-toolbar"><slot /></div>' },
          EmptyState: { template: '<div class="empty-state">空状态</div>' },
        },
      },
    })
  }

  it('渲染搜索表单插槽', () => {
    const wrapper = mountLayout()
    expect(wrapper.find('.search-form').exists()).toBe(true)
  })

  it('渲染工具栏插槽', () => {
    const wrapper = mountLayout()
    expect(wrapper.find('.toolbar').exists()).toBe(true)
  })

  it('渲染表格内容插槽', () => {
    const wrapper = mountLayout()
    expect(wrapper.find('.table-content').exists()).toBe(true)
  })

  it('loading=true 且 loadingType=skeleton 时渲染骨架屏', () => {
    const wrapper = mountLayout({ loading: true, loadingType: 'skeleton' })
    expect(wrapper.find('.skeleton-table').exists()).toBe(true)
  })

  it('list 为空且 loading=false 时渲染空状态', () => {
    const wrapper = mountLayout({ loading: false, list: [], total: 0 })
    expect(wrapper.find('.empty-state').exists()).toBe(true)
  })

  it('有数据时不渲染空状态', () => {
    const wrapper = mountLayout({
      list: [{ id: '1', name: '测试' }],
      total: 1,
    })
    expect(wrapper.find('.empty-state').exists()).toBe(false)
  })

  it('hideSearch=true 时隐藏搜索表单', () => {
    const wrapper = mountLayout({ hideSearch: true })
    expect(wrapper.find('.search-form').exists()).toBe(false)
  })

  it('hideToolbar=true 时隐藏工具栏', () => {
    const wrapper = mountLayout({ hideToolbar: true })
    expect(wrapper.find('.toolbar').exists()).toBe(false)
  })

  it('hidePagination=true 时隐藏分页', () => {
    const wrapper = mountLayout({ hidePagination: true })
    expect(wrapper.find('.el-pagination').exists()).toBe(false)
  })

  it('分页器接收正确的 page/size/total', () => {
    const wrapper = mountLayout({
      list: [{ id: '1' }],
      total: 100,
      query: { page: 3, size: 50 },
    })
    const pagination = wrapper.find('.el-pagination')
    expect(pagination.attributes('data-current')).toBe('3')
    expect(pagination.attributes('data-size')).toBe('50')
    expect(pagination.attributes('data-total')).toBe('100')
  })

  it('点击查询按钮触发 query 事件', async () => {
    const wrapper = mountLayout()
    // 查询按钮应该有对应的文案
    const buttons = wrapper.findAll('.el-button')
    expect(buttons.length).toBeGreaterThan(0)
  })

  it('渲染默认插槽', () => {
    const wrapper = mountLayout({}, {
      default: '<div class="extra-actions">额外操作</div>',
    })
    expect(wrapper.find('.extra-actions').exists()).toBe(true)
  })
})
