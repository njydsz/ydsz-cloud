/**
 * @file LanguageSwitcher 语言切换组件 单元测试 (批次 20 P2-2)
 * @description 验证当前 locale 缩写渲染、supportedLocales 下拉列表、
 *   点击切换 setLocale、当前项禁用及切换后禁用态互换等行为.
 * @module components/common/__tests__/LanguageSwitcher
 *
 * 验证:
 *   1. 渲染时显示当前 locale 缩写
 *   2. 下拉菜单中列出 supportedLocales
 *   3. 点击某项后触发 setLocale
 *   4. 当前项被禁用
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// 直接 stub 关键 Element Plus 组件以避免 teleport 渲染问题
const ElDropdown = {
  name: 'ElDropdown',
  emits: ['command'],
  template:
    '<div class="el-dropdown" @click="$emit(\'command\', $event.target.dataset.command)"><slot /><slot name="dropdown" /></div>',
}
const ElDropdownMenu = {
  name: 'ElDropdownMenu',
  template: '<div class="el-dropdown-menu"><slot /></div>',
}
const ElDropdownItem = {
  name: 'ElDropdownItem',
  emits: ['command'],
  props: ['command', 'disabled'],
  template:
    '<div class="el-dropdown-item" :data-disabled="disabled" :data-command="command" @click="$emit(\'command\', command)"><slot /></div>',
}
const ElButton = { name: 'ElButton', template: '<button><slot /></button>' }
const ElIcon = { name: 'ElIcon', template: '<i><slot /></i>' }
const ElTooltip = { name: 'ElTooltip', template: '<div><slot /></div>' }
const Position = { name: 'Position', template: '<i />' }
const Check = { name: 'Check', template: '<i />' }

import LanguageSwitcher from '../LanguageSwitcher.vue'
import { setLocale as resetLocale } from '@/composables/useI18n'

describe('LanguageSwitcher', () => {
  beforeEach(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.clear()
    }
    // 重置 module-level 的 locale singleton
    resetLocale('zh-CN')
    vi.resetModules()
  })

  it('渲染当前 locale 缩写', async () => {
    const wrapper = mount(LanguageSwitcher, {
      global: {
        components: {
          ElDropdown,
          ElDropdownMenu,
          ElDropdownItem,
          ElButton,
          ElIcon,
          ElTooltip,
          Position,
          Check,
        },
        stubs: { teleport: true },
      },
    })
    expect(wrapper.find('.lang-label').text()).toBe('zh-CN')
  })

  it('下拉菜单至少渲染 zh-CN 和 en-US 两项', async () => {
    const wrapper = mount(LanguageSwitcher, {
      global: {
        components: {
          ElDropdown,
          ElDropdownMenu,
          ElDropdownItem,
          ElButton,
          ElIcon,
          ElTooltip,
          Position,
          Check,
        },
        stubs: { teleport: true },
      },
    })
    const items = wrapper.findAllComponents(ElDropdownItem)
    expect(items.length).toBeGreaterThanOrEqual(2)
    const codes = items.map((i) => i.props('command'))
    expect(codes).toContain('zh-CN')
    expect(codes).toContain('en-US')
  })

  it('点击 en-US 后切换 locale', async () => {
    const wrapper = mount(LanguageSwitcher, {
      global: {
        components: {
          ElDropdown,
          ElDropdownMenu,
          ElDropdownItem,
          ElButton,
          ElIcon,
          ElTooltip,
          Position,
          Check,
        },
        stubs: { teleport: true },
      },
    })
    const items = wrapper.findAllComponents(ElDropdownItem)
    const enItem = items.find((i) => i.props('command') === 'en-US')!
    await enItem.trigger('click')
    expect(wrapper.find('.lang-label').text()).toBe('en-US')
  })

  it('当前 locale 项被 disabled', async () => {
    const wrapper = mount(LanguageSwitcher, {
      global: {
        components: {
          ElDropdown,
          ElDropdownMenu,
          ElDropdownItem,
          ElButton,
          ElIcon,
          ElTooltip,
          Position,
          Check,
        },
        stubs: { teleport: true },
      },
    })
    const items = wrapper.findAllComponents(ElDropdownItem)
    const zhItem = items.find((i) => i.props('command') === 'zh-CN')!
    // 默认 locale 是 zh-CN, 应当被禁用
    expect(zhItem.props('disabled')).toBe(true)
  })

  it('切换后原 locale 启用, 新 locale 禁用', async () => {
    const wrapper = mount(LanguageSwitcher, {
      global: {
        components: {
          ElDropdown,
          ElDropdownMenu,
          ElDropdownItem,
          ElButton,
          ElIcon,
          ElTooltip,
          Position,
          Check,
        },
        stubs: { teleport: true },
      },
    })
    const items = wrapper.findAllComponents(ElDropdownItem)
    const enItem = items.find((i) => i.props('command') === 'en-US')!
    await enItem.trigger('click')
    // 切换到 en-US 后
    const itemsAfter = wrapper.findAllComponents(ElDropdownItem)
    const enAfter = itemsAfter.find((i) => i.props('command') === 'en-US')!
    const zhAfter = itemsAfter.find((i) => i.props('command') === 'zh-CN')!
    expect(enAfter.props('disabled')).toBe(true)
    expect(zhAfter.props('disabled')).toBeFalsy()
  })
})
