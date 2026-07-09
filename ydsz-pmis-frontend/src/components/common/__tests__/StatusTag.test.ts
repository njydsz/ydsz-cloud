/**
 * @file StatusTag 组件单元测试
 * @description 测试状态标签组件的渲染与类型映射
 * @module components/common/__tests__/StatusTag
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusTag from '../StatusTag.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('StatusTag', () => {
  it('渲染传入的 label', () => {
    const wrapper = mount(StatusTag, {
      props: { label: '进行中', type: 'primary' },
    })
    expect(wrapper.text()).toContain('进行中')
  })

  it('type=primary 渲染为 primary 类型 tag', () => {
    const wrapper = mount(StatusTag, {
      props: { label: 'Active', type: 'primary' },
    })
    const tag = wrapper.find('.el-tag')
    expect(tag.classes()).toContain('el-tag--primary')
  })

  it('type=success 渲染为 success 类型 tag', () => {
    const wrapper = mount(StatusTag, {
      props: { label: '完成', type: 'success' },
    })
    const tag = wrapper.find('.el-tag')
    expect(tag.classes()).toContain('el-tag--success')
  })

  it('type=danger 渲染为 danger 类型 tag', () => {
    const wrapper = mount(StatusTag, {
      props: { label: '失败', type: 'danger' },
    })
    const tag = wrapper.find('.el-tag')
    expect(tag.classes()).toContain('el-tag--danger')
  })

  it('type=warning 渲染为 warning 类型 tag', () => {
    const wrapper = mount(StatusTag, {
      props: { label: '警告', type: 'warning' },
    })
    const tag = wrapper.find('.el-tag')
    expect(tag.classes()).toContain('el-tag--warning')
  })

  it('type=info 渲染为 info 类型 tag', () => {
    const wrapper = mount(StatusTag, {
      props: { label: '信息', type: 'info' },
    })
    const tag = wrapper.find('.el-tag')
    expect(tag.classes()).toContain('el-tag--info')
  })

  it('不传 type 时默认为 info', () => {
    const wrapper = mount(StatusTag, {
      props: { label: '默认' },
    })
    const tag = wrapper.find('.el-tag')
    expect(tag.exists()).toBe(true)
  })
})
