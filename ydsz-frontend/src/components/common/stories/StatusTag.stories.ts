/**
 * @file StatusTag 组件 Story
 * @description P2-8: StatusTag 组件的可视化文档与交互测试
 */
import type { Meta, StoryObj } from '@storybook/vue3'
import StatusTag from '../common/StatusTag.vue'

const meta: Meta<typeof StatusTag> = {
  title: 'Common/StatusTag',
  component: StatusTag,
  tags: ['autodocs'],
  argTypes: {
    type: {
      control: 'select',
      options: ['primary', 'success', 'warning', 'danger', 'info'],
      description: '标签类型',
    },
    size: {
      control: 'select',
      options: ['small', 'default', 'large'],
      description: '标签尺寸',
    },
    effect: {
      control: 'select',
      options: ['light', 'dark', 'plain'],
      description: '标签效果',
    },
    label: {
      control: 'text',
      description: '标签文本',
    },
  },
}

export default meta
type Story = StoryObj<typeof StatusTag>

export const Primary: Story = {
  args: {
    label: '进行中',
    type: 'primary',
  },
}

export const Success: Story = {
  args: {
    label: '已完成',
    type: 'success',
  },
}

export const Warning: Story = {
  args: {
    label: '待处理',
    type: 'warning',
  },
}

export const Danger: Story = {
  args: {
    label: '已驳回',
    type: 'danger',
  },
}

export const Info: Story = {
  args: {
    label: '草稿',
    type: 'info',
  },
}

export const WithMap: Story = {
  args: {
    value: 'ACTIVE',
    map: {
      ACTIVE: { label: '进行中', type: 'primary' },
      CLOSED: { label: '已关闭', type: 'info' },
      PENDING: { label: '待审批', type: 'warning' },
      REJECTED: { label: '已驳回', type: 'danger' },
    },
  },
  render: (args) => ({
    components: { StatusTag },
    setup() {
      return { args }
    },
    template: `
      <div style="display: flex; gap: 8px; flex-wrap: wrap;">
        <StatusTag v-bind="args" value="ACTIVE" />
        <StatusTag v-bind="args" value="CLOSED" />
        <StatusTag v-bind="args" value="PENDING" />
        <StatusTag v-bind="args" value="REJECTED" />
      </div>
    `,
  }),
}

export const AllTypes: Story = {
  render: () => ({
    components: { StatusTag },
    template: `
      <div style="display: flex; gap: 8px; flex-wrap: wrap;">
        <StatusTag label="Primary" type="primary" />
        <StatusTag label="Success" type="success" />
        <StatusTag label="Warning" type="warning" />
        <StatusTag label="Danger" type="danger" />
        <StatusTag label="Info" type="info" />
      </div>
    `,
  }),
}

export const AllSizes: Story = {
  render: () => ({
    components: { StatusTag },
    template: `
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
        <StatusTag label="Small" type="primary" size="small" />
        <StatusTag label="Default" type="primary" size="default" />
        <StatusTag label="Large" type="primary" size="large" />
      </div>
    `,
  }),
}
