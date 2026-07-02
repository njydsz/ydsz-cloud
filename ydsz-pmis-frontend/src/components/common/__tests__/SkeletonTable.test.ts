/**
 * @file 骨架屏组件 单元测试（P1-7）
 * @description 覆盖 SkeletonTable / SkeletonCard / SkeletonDetail 三个骨架组件的
 *   行列渲染、默认 props、卡片数量、详情骨架条目等场景.
 * @module components/common/__tests__/SkeletonTable
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SkeletonTable from '../SkeletonTable.vue'
import SkeletonCard from '../SkeletonCard.vue'
import SkeletonDetail from '../SkeletonDetail.vue'

describe('SkeletonTable', () => {
  it('should render correct number of rows and columns', () => {
    const wrapper = mount(SkeletonTable, { props: { rows: 3, columns: 4 } })
    const rows = wrapper.findAll('.skeleton-row')
    expect(rows).toHaveLength(3)
    expect(wrapper.findAll('.skeleton-header .el-skeleton__item')).toHaveLength(4)
  })

  it('should use default props', () => {
    const wrapper = mount(SkeletonTable)
    expect(wrapper.findAll('.skeleton-row')).toHaveLength(5)
  })
})

describe('SkeletonCard', () => {
  it('should render correct number of cards', () => {
    const wrapper = mount(SkeletonCard, { props: { count: 3 } })
    expect(wrapper.findAllComponents({ name: 'ElCard' })).toHaveLength(3)
  })
})

describe('SkeletonDetail', () => {
  it('should render with default rows', () => {
    const wrapper = mount(SkeletonDetail)
    expect(wrapper.findComponent({ name: 'ElSkeleton' }).exists()).toBe(true)
  })

  it('should render with custom rows', () => {
    const wrapper = mount(SkeletonDetail, { props: { rows: 5 } })
    const items = wrapper.findAll('.el-skeleton__item')
    expect(items.length).toBeGreaterThan(0)
  })
})
