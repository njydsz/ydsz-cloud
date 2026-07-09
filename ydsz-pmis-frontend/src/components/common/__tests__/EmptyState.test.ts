/**
 * @file EmptyState 组件单元测试
 * @description 测试空状态组件的预设场景、自定义内容、CTA 按钮等
 * @module components/common/__tests__/EmptyState
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../EmptyState.vue'

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'common.emptyState.list.title': '暂无数据',
        'common.emptyState.list.description': '还没有任何数据',
        'common.emptyState.search.title': '搜索无结果',
        'common.emptyState.search.description': '没有找到匹配的结果',
        'common.emptyState.network.title': '网络异常',
        'common.emptyState.network.description': '请检查网络连接',
        'common.emptyState.noPermission.title': '无权限',
        'common.emptyState.noPermission.description': '您没有权限查看此内容',
        'common.empty': '空',
      }
      return map[key] || key
    },
  }),
}))

describe('EmptyState', () => {
  it('默认使用 list 预设', () => {
    const wrapper = mount(EmptyState)
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('使用 search 预设', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'search' },
    })
    expect(wrapper.text()).toContain('搜索无结果')
  })

  it('使用 network 预设', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'network' },
    })
    expect(wrapper.text()).toContain('网络异常')
  })

  it('使用 noPermission 预设', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'noPermission' },
    })
    expect(wrapper.text()).toContain('无权限')
  })

  it('custom 预设使用自定义标题和描述', () => {
    const wrapper = mount(EmptyState, {
      props: {
        preset: 'custom',
        title: '自定义标题',
        description: '自定义描述内容',
      },
    })
    expect(wrapper.text()).toContain('自定义标题')
    expect(wrapper.text()).toContain('自定义描述内容')
  })

  it('actionText 显示 CTA 按钮并触发 action 事件', async () => {
    const wrapper = mount(EmptyState, {
      props: {
        actionText: '新建项目',
      },
    })
    const button = wrapper.find('button')
    expect(button.exists()).toBe(true)
    expect(button.text()).toContain('新建项目')

    await button.trigger('click')
    expect(wrapper.emitted('action')).toBeTruthy()
    expect(wrapper.emitted('action')!.length).toBe(1)
  })

  it('不传 actionText 时不显示 CTA 按钮', () => {
    const wrapper = mount(EmptyState)
    const button = wrapper.find('button')
    expect(button.exists()).toBe(false)
  })

  it('支持 action 插槽', () => {
    const wrapper = mount(EmptyState, {
      slots: {
        action: '<button class="custom-btn">自定义按钮</button>',
      },
    })
    expect(wrapper.find('.custom-btn').exists()).toBe(true)
  })

  it('blockHeight 设置容器高度', () => {
    const wrapper = mount(EmptyState, {
      props: { blockHeight: 300 },
    })
    const container = wrapper.find('.empty-state')
    expect(container.attributes('style')).toContain('height: 300px')
  })

  it('imageUrl 优先于 icon 显示', () => {
    const wrapper = mount(EmptyState, {
      props: {
        imageUrl: 'https://example.com/empty.png',
      },
    })
    expect(wrapper.find('img').exists()).toBe(true)
    expect(wrapper.find('.empty-state__icon').exists()).toBe(false)
  })
})
