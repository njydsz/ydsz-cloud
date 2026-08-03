/**
 * Storybook 主配置
 *
 * P1-2.3: 组件文档化 — 为高频 UI 组件提供交互式文档
 *
 * @path .storybook/main.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { StorybookConfig } from '@storybook/vue3-vite';

const config: StorybookConfig = {
  stories: [
    '../comm/@core/ui-kit/shadcn-ui/src/**/*.stories.@(js|jsx|ts|tsx)',
    '../comm/@core/ui-kit/form-ui/src/**/*.stories.@(js|jsx|ts|tsx)',
    '../comm/@core/ui-kit/popup-ui/src/**/*.stories.@(js|jsx|ts|tsx)',
    '../comm/@core/ui-kit/tabs-ui/src/**/*.stories.@(js|jsx|ts|tsx)',
    '../comm/effects/common-ui/src/**/*.stories.@(js|jsx|ts|tsx)',
  ],
  addons: [
    '@storybook/addon-links',
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
  viteFinal: async (config) => {
    return {
      ...config,
      resolve: {
        ...config.resolve,
        alias: {
          ...config.resolve?.alias,
          '@': '/comm/@core/ui-kit/shadcn-ui/src',
        },
      },
    };
  },
};

export default config;
