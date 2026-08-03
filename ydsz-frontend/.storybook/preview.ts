/**
 * Storybook 预览配置
 *
 * P1-2.3: 组件文档化 — 全局装饰器和参数配置
 *
 * @path .storybook/preview.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Preview } from '@storybook/vue3';

import { setup } from '@storybook/vue3';

// 全局样式导入
import '../comm/styles/src/index.css';

// Vue 插件设置
setup((app) => {
  // 可以在这里注册全局插件、指令等
});

const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/,
      },
    },
    backgrounds: {
      default: 'light',
      values: [
        { name: 'light', value: '#ffffff' },
        { name: 'dark', value: '#1a1a1a' },
        { name: 'gray', value: '#f5f5f5' },
      ],
    },
    layout: 'centered',
  },
};

export default preview;
