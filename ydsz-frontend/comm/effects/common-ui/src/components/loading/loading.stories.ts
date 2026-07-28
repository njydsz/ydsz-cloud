/**
 * YDSZ 公共组件 Storybook 示例 — Loading 组件
 *
 * P2-3: Storybook 组件文档
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Meta, StoryObj } from '@storybook/vue3';

import Loading from '../loading.vue';

const meta: Meta<typeof Loading> = {
  title: 'Common/Loading',
  component: Loading,
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['small', 'default', 'large'],
    },
    text: {
      control: 'text',
    },
  },
};

export default meta;
type Story = StoryObj<typeof Loading>;

export const Default: Story = {
  args: {
    size: 'default',
    text: '加载中...',
  },
};

export const Small: Story = {
  args: {
    size: 'small',
    text: '加载中...',
  },
};

export const Large: Story = {
  args: {
    size: 'large',
    text: '加载中...',
  },
};

export const WithoutText: Story = {
  args: {
    size: 'default',
    text: '',
  },
};
