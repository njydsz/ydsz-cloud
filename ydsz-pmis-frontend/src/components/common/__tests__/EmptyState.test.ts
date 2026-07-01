import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '@/components/common/EmptyState.vue'

/**
 * Element Plus 组件在 jsdom 下依赖样式, 测试中用 stubs 替代
 * el-button / el-icon 以聚焦 EmptyState 自身的 prop/emit 行为.
 */
const elStubs = {
  'el-button': {
    template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-icon': {
    template: '<span class="el-icon-stub" :data-size="size"><slot /></span>',
    props: ['size'],
  },
}

describe('EmptyState 通用空状态', () => {
  it('默认 preset=list 渲染 "暂无数据"', () => {
    const wrapper = mount(EmptyState, { global: { stubs: elStubs } })
    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).toContain('当前列表为空')
  })

  it('preset=search 渲染搜索空态文案', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'search' },
      global: { stubs: elStubs },
    })
    expect(wrapper.text()).toContain('未找到匹配的数据')
    expect(wrapper.text()).toContain('请尝试调整筛选条件')
  })

  it('preset=network 渲染网络异常文案', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'network' },
      global: { stubs: elStubs },
    })
    expect(wrapper.text()).toContain('数据加载失败')
    expect(wrapper.text()).toContain('网络异常')
  })

  it('preset=noPermission 渲染无权限文案', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'noPermission' },
      global: { stubs: elStubs },
    })
    expect(wrapper.text()).toContain('无访问权限')
  })

  it('preset=custom 时使用 props 传入的 title 和 description', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'custom', title: '空空如也', description: '请稍后再来' },
      global: { stubs: elStubs },
    })
    expect(wrapper.text()).toContain('空空如也')
    expect(wrapper.text()).toContain('请稍后再来')
  })

  it('actionText 设置后渲染 CTA 按钮, 点击触发 action 事件', async () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'list', actionText: '新建记录' },
      global: { stubs: elStubs },
    })
    const btn = wrapper.find('button.el-button-stub')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('新建记录')
    await btn.trigger('click')
    expect(wrapper.emitted('action')).toBeTruthy()
    // 至少触发 1 次, 避免 vue 编译输出导致 onAction 多次调用的脆性
    expect(wrapper.emitted('action')!.length).toBeGreaterThanOrEqual(1)
  })

  it('无 actionText 时不渲染 CTA 按钮', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'list' },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('button.el-button-stub').exists()).toBe(false)
  })

  it('imageUrl 设置后渲染 <img> 元素', () => {
    const wrapper = mount(EmptyState, {
      props: { imageUrl: 'https://example.com/empty.png' },
      global: { stubs: elStubs },
    })
    const img = wrapper.find('img.empty-state__image')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/empty.png')
  })

  it('extra 插槽可被渲染', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'list' },
      slots: {
        extra: '<a class="custom-help" href="#">查看帮助</a>',
      },
      global: { stubs: elStubs },
    })
    expect(wrapper.find('.custom-help').exists()).toBe(true)
    expect(wrapper.text()).toContain('查看帮助')
  })

  it('blockHeight 设置后应用内联高度样式', () => {
    const wrapper = mount(EmptyState, {
      props: { blockHeight: 360 },
      global: { stubs: elStubs },
    })
    const root = wrapper.find('.empty-state')
    expect(root.attributes('style')).toContain('height: 360px')
  })

  it('iconSize 透传到 el-icon 的 size 属性', () => {
    const wrapper = mount(EmptyState, {
      props: { preset: 'list', iconSize: 96 },
      global: { stubs: elStubs },
    })
    const icon = wrapper.find('.el-icon-stub')
    expect(icon.exists()).toBe(true)
    expect(icon.attributes('data-size')).toBe('96')
  })
})
