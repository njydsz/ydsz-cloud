/**
 * Input 组件 Storybook Stories
 *
 * P1-2.3: 组件文档化 — Input 组件交互式文档
 *
 * @path comm/@core/ui-kit/shadcn-ui/src/ui/input/Input.stories.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Meta, StoryObj } from '@storybook/vue3';

import Input from './Input.vue';

const meta: Meta<typeof Input> = {
  title: 'Core/Input',
  component: Input,
  tags: ['autodocs'],
  argTypes: {
    type: {
      control: 'select',
      options: ['text', 'password', 'email', 'number', 'tel', 'url', 'search'],
      description: '输入框类型',
    },
    placeholder: {
      control: 'text',
      description: '占位文本',
    },
    disabled: {
      control: 'boolean',
      description: '是否禁用',
    },
    modelValue: {
      control: 'text',
      description: '绑定值',
    },
  },
  parameters: {
    docs: {
      description: {
        component: '输入框组件用于接收用户文本输入，支持多种类型和状态。',
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof Input>;

/** 默认输入框 */
export const Default: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'text',
    placeholder: '请输入内容...',
    modelValue: '',
  },
};

/** 密码输入框 */
export const Password: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'password',
    placeholder: '请输入密码...',
    modelValue: '',
  },
};

/** 邮箱输入框 */
export const Email: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'email',
    placeholder: '请输入邮箱...',
    modelValue: '',
  },
};

/** 数字输入框 */
export const Number: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'number',
    placeholder: '请输入数字...',
    modelValue: '',
  },
};

/** 禁用状态 */
export const Disabled: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'text',
    placeholder: '禁用状态',
    modelValue: '无法编辑',
    disabled: true,
  },
};

/** 带默认值 */
export const WithValue: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'text',
    placeholder: '请输入内容...',
    modelValue: '默认值',
  },
};

/** 搜索输入框 */
export const Search: Story = {
  render: (args) => ({
    components: { Input },
    setup() {
      return { args };
    },
    template: '<Input v-bind="args" v-model="args.modelValue" style="width: 300px;" />',
  }),
  args: {
    type: 'search',
    placeholder: '搜索...',
    modelValue: '',
  },
};

/** 所有类型展示 */
export const AllTypes: Story = {
  render: () => ({
    components: { Input },
    template: `
      <div style="display: flex; flex-direction: column; gap: 16px; width: 300px;">
        <Input type="text" placeholder="文本输入" />
        <Input type="password" placeholder="密码输入" />
        <Input type="email" placeholder="邮箱输入" />
        <Input type="number" placeholder="数字输入" />
        <Input type="tel" placeholder="电话输入" />
        <Input type="url" placeholder="URL 输入" />
        <Input type="search" placeholder="搜索输入" />
      </div>
    `,
  }),
};
