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

  it('value 不在 map 中时回退为 "-"', () => {
    const wrapper = mount(StatusTag, {
      props: {
        value: 'UNKNOWN',
        map: { PLANNED: { label: '计划中' } },
        fallbackType: 'warning',
      },
    })
    expect(wrapper.text()).toContain('-')
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
