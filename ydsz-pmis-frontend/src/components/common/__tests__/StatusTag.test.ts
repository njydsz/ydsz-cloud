/**
 * @file StatusTag 通用状态标签组件 单元测试
 * @description 覆盖 map 匹配渲染、value 缺失回退 "-"、显式 label/type 优先级等场景.
 * @module components/common/__tests__/StatusTag
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusTag from '@/components/common/StatusTag.vue'

describe('StatusTag 通用状态标签', () => {
  it('通过 map 匹配 value, 显示对应 label 与 type', () => {
    const wrapper = mount(StatusTag, {
      props: {
        value: 'PLANNED',
        map: {
          PLANNED: { label: '计划中', type: 'info' },
          DONE: { label: '已完成', type: 'success' },
        },
      },
    })
    expect(wrapper.text()).toContain('计划中')
  })

  it('value 不在 map 中时回退显示原值并使用 fallbackType', () => {
    const wrapper = mount(StatusTag, {
      props: {
        value: 'UNKNOWN',
        map: { PLANNED: { label: '计划中' } },
        fallbackType: 'warning',
      },
    })
    // 原值是非空字符串时, label 显示原值, 避免无意义 "-" 噪音
    expect(wrapper.text()).toContain('UNKNOWN')
  })

  it('value 为 null / undefined 时显示 "-"', () => {
    const nullWrapper = mount(StatusTag, { props: { value: null, map: {} } })
    const undefWrapper = mount(StatusTag, { props: { value: undefined, map: {} } })
    expect(nullWrapper.text()).toContain('-')
    expect(undefWrapper.text()).toContain('-')
  })

  it('显式传入 label / type 时优先使用 props', () => {
    const wrapper = mount(StatusTag, {
      props: { value: 'X', label: '已通过', type: 'success' },
    })
    expect(wrapper.text()).toContain('已通过')
  })

  it('空字符串 value 也回退为 "-"', () => {
    const wrapper = mount(StatusTag, { props: { value: '', map: {} } })
    expect(wrapper.text()).toContain('-')
  })
})
