/**
 * YDSZ Storybook 配置
 *
 * P2-3: 前端公共组件文档 + 交互式 Demo
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { StorybookConfig } from '@storybook/vue3-vite';

const config: StorybookConfig = {
  stories: [
    '../comm/effects/common-ui/src/**/*.stories.@(ts|tsx|js|jsx)',
    '../comm/@core/ui-kit/**/src/**/*.stories.@(ts|tsx|js|jsx)',
  ],
  addons: [
    '@storybook/addon-essentials',
    '@storybook/addon-interactions',
    '@storybook/addon-a11y',
  ],
  framework: {
    name: '@storybook/vue3-vite',
    options: {},
  },
  docs: {
    autodocs: 'tag',
  },
  staticDirs: ['../comm/@core/base/shared/src/assets'],
};

export default config;
