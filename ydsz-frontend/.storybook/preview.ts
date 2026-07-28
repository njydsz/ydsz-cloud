/**
 * YDSZ Storybook 预览配置
 *
 * P2-3: 全局样式 + Element Plus 主题注入
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Preview } from '@storybook/vue3';

import 'element-plus/dist/index.css';
import '../comm/styles/src/index.css';

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
        { name: 'light', value: '#ffffff' },
        { name: 'dark', value: '#1a1a1a' },
        { name: 'ydsz-bg', value: '#f5f7fa' },
      ],
    },
  },
};

export default preview;
