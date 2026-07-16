/**
 * @file Storybook 全局预览配置
 * @description P2-8: 全局样式注入、主题切换、i18n 支持
 */
import type { Preview } from '@storybook/vue3'
import { setup } from '@storybook/vue3'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createI18n } from 'vue-i18n'
import '@/styles/index.scss'

// 初始化 i18n（Storybook 环境）
const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': {
      common: {
        ok: '确定',
        cancel: '取消',
        save: '保存',
        search: '搜索',
        reset: '重置',
      },
    },
  },
})

setup((app) => {
  app.use(ElementPlus)
  app.use(i18n)
})

const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    layout: 'centered',
    backgrounds: {
      default: 'light',
      values: [
        { name: 'light', value: '#f5f7fa' },
        { name: 'dark', value: '#1d2129' },
        { name: 'white', value: '#ffffff' },
      ],
    },
    a11y: {
      config: {
        rules: [
          {
            // 按钮必须有可访问名称
            id: 'button-name',
            enabled: true,
          },
          {
            // 颜色对比度
            id: 'color-contrast',
            enabled: true,
          },
        ],
      },
    },
  },
  globalTypes: {
    theme: {
      name: '主题',
      description: '主题切换',
      defaultValue: 'light',
      toolbar: {
        icon: 'circlehollow',
        items: [
          { value: 'light', icon: 'circlehollow', title: '浅色' },
          { value: 'dark', icon: 'circle', title: '深色' },
        ],
      },
    },
    locale: {
      name: '语言',
      description: '界面语言',
      defaultValue: 'zh-CN',
      toolbar: {
        icon: 'globe',
        items: [
          { value: 'zh-CN', title: '中文' },
          { value: 'en-US', title: 'English' },
        ],
      },
    },
  },
}

export default preview
