/**
 * Tabs 组件 Storybook Stories
 *
 * P1-2.3: 组件文档化 — Tabs 组件交互式文档
 *
 * @path comm/@core/ui-kit/shadcn-ui/src/ui/tabs/Tabs.stories.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Meta, StoryObj } from '@storybook/vue3';

import { Tabs, TabsContent, TabsList, TabsTrigger } from './index';

const meta: Meta = {
  title: 'Core/Tabs',
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: '标签页组件用于在同一区域内切换不同的内容视图，支持多个标签页的组织和管理。',
      },
    },
  },
};

export default meta;
type Story = StoryObj;

/** 基础标签页 */
export const Default: Story = {
  render: () => ({
    components: { Tabs, TabsList, TabsTrigger, TabsContent },
    template: `
      <Tabs default-value="tab1" style="width: 500px;">
        <TabsList>
          <TabsTrigger value="tab1">标签一</TabsTrigger>
          <TabsTrigger value="tab2">标签二</TabsTrigger>
          <TabsTrigger value="tab3">标签三</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <p>这是标签一的内容区域。</p>
          </div>
        </TabsContent>
        <TabsContent value="tab2">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <p>这是标签二的内容区域。</p>
          </div>
        </TabsContent>
        <TabsContent value="tab3">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <p>这是标签三的内容区域。</p>
          </div>
        </TabsContent>
      </Tabs>
    `,
  }),
};

/** 带默认激活标签 */
export const WithDefaultTab: Story = {
  render: () => ({
    components: { Tabs, TabsList, TabsTrigger, TabsContent },
    template: `
      <Tabs default-value="settings" style="width: 500px;">
        <TabsList>
          <TabsTrigger value="account">账户</TabsTrigger>
          <TabsTrigger value="settings">设置</TabsTrigger>
          <TabsTrigger value="notifications">通知</TabsTrigger>
        </TabsList>
        <TabsContent value="account">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <h3>账户设置</h3>
            <p>管理您的账户信息和偏好设置。</p>
          </div>
        </TabsContent>
        <TabsContent value="settings">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <h3>系统设置</h3>
            <p>配置系统参数和选项。</p>
          </div>
        </TabsContent>
        <TabsContent value="notifications">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <h3>通知设置</h3>
            <p>自定义通知偏好和提醒方式。</p>
          </div>
        </TabsContent>
      </Tabs>
    `,
  }),
};

/** 禁用某个标签 */
export const WithDisabledTab: Story = {
  render: () => ({
    components: { Tabs, TabsList, TabsTrigger, TabsContent },
    template: `
      <Tabs default-value="tab1" style="width: 500px;">
        <TabsList>
          <TabsTrigger value="tab1">可用标签</TabsTrigger>
          <TabsTrigger value="tab2" disabled>禁用标签</TabsTrigger>
          <TabsTrigger value="tab3">另一个可用标签</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <p>这是第一个标签的内容。</p>
          </div>
        </TabsContent>
        <TabsContent value="tab3">
          <div style="padding: 16px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <p>这是第三个标签的内容。</p>
          </div>
        </TabsContent>
      </Tabs>
    `,
  }),
};

/** 复杂内容标签页 */
export const WithComplexContent: Story = {
  render: () => ({
    components: { Tabs, TabsList, TabsTrigger, TabsContent },
    template: `
      <Tabs default-value="overview" style="width: 600px;">
        <TabsList>
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="analytics">分析</TabsTrigger>
          <TabsTrigger value="reports">报告</TabsTrigger>
        </TabsList>
        <TabsContent value="overview">
          <div style="padding: 20px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <h3>项目概览</h3>
            <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 16px;">
              <div style="padding: 16px; background: #f3f4f6; border-radius: 4px;">
                <p style="font-size: 14px; color: #6b7280;">总项目数</p>
                <p style="font-size: 24px; font-weight: bold; margin-top: 4px;">128</p>
              </div>
              <div style="padding: 16px; background: #f3f4f6; border-radius: 4px;">
                <p style="font-size: 14px; color: #6b7280;">进行中</p>
                <p style="font-size: 24px; font-weight: bold; margin-top: 4px;">42</p>
              </div>
              <div style="padding: 16px; background: #f3f4f6; border-radius: 4px;">
                <p style="font-size: 14px; color: #6b7280;">已完成</p>
                <p style="font-size: 24px; font-weight: bold; margin-top: 4px;">86</p>
              </div>
            </div>
          </div>
        </TabsContent>
        <TabsContent value="analytics">
          <div style="padding: 20px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <h3>数据分析</h3>
            <p style="margin-top: 12px;">这里可以展示各种图表和数据可视化内容。</p>
          </div>
        </TabsContent>
        <TabsContent value="reports">
          <div style="padding: 20px; border: 1px solid #e5e7eb; border-radius: 4px; margin-top: 8px;">
            <h3>报告列表</h3>
            <ul style="margin-top: 12px; list-style: none; padding: 0;">
              <li style="padding: 8px 0; border-bottom: 1px solid #e5e7eb;">月度报告 - 2024年1月</li>
              <li style="padding: 8px 0; border-bottom: 1px solid #e5e7eb;">季度报告 - 2023年Q4</li>
              <li style="padding: 8px 0;">年度报告 - 2023年</li>
            </ul>
          </div>
        </TabsContent>
      </Tabs>
    `,
  }),
};
