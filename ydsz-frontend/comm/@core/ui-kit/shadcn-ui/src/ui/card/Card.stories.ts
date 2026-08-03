/**
 * Card 组件 Storybook Stories
 *
 * P1-2.3: 组件文档化 — Card 组件交互式文档
 *
 * @path comm/@core/ui-kit/shadcn-ui/src/ui/card/Card.stories.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Meta, StoryObj } from '@storybook/vue3';

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from './index';

const meta: Meta = {
  title: 'Core/Card',
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: '卡片组件用于组织和展示相关内容，支持标题、描述、内容和底部区域。',
      },
    },
  },
};

export default meta;
type Story = StoryObj;

/** 基础卡片 */
export const Default: Story = {
  render: () => ({
    components: { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter },
    template: `
      <Card style="width: 350px;">
        <CardHeader>
          <CardTitle>卡片标题</CardTitle>
          <CardDescription>卡片描述文本</CardDescription>
        </CardHeader>
        <CardContent>
          <p>这是卡片的主要内容区域。</p>
        </CardContent>
        <CardFooter>
          <p>卡片底部</p>
        </CardFooter>
      </Card>
    `,
  }),
};

/** 仅标题卡片 */
export const WithTitle: Story = {
  render: () => ({
    components: { Card, CardHeader, CardTitle, CardContent },
    template: `
      <Card style="width: 350px;">
        <CardHeader>
          <CardTitle>项目概览</CardTitle>
        </CardHeader>
        <CardContent>
          <p>这是一个简单的卡片示例，只包含标题和内容。</p>
        </CardContent>
      </Card>
    `,
  }),
};

/** 完整卡片（带所有区域） */
export const FullCard: Story = {
  render: () => ({
    components: { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter },
    template: `
      <Card style="width: 400px;">
        <CardHeader>
          <CardTitle>用户信息</CardTitle>
          <CardDescription>查看和编辑用户资料</CardDescription>
        </CardHeader>
        <CardContent>
          <div style="display: flex; flex-direction: column; gap: 8px;">
            <p><strong>姓名：</strong>张三</p>
            <p><strong>邮箱：</strong>zhangsan@example.com</p>
            <p><strong>角色：</strong>管理员</p>
          </div>
        </CardContent>
        <CardFooter style="display: flex; gap: 8px; justify-content: flex-end;">
          <button style="padding: 8px 16px; border: 1px solid #ccc; border-radius: 4px; background: white;">取消</button>
          <button style="padding: 8px 16px; border: none; border-radius: 4px; background: #007bff; color: white;">保存</button>
        </CardFooter>
      </Card>
    `,
  }),
};

/** 多卡片布局 */
export const CardGrid: Story = {
  render: () => ({
    components: { Card, CardHeader, CardTitle, CardDescription, CardContent },
    template: `
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; max-width: 900px;">
        <Card>
          <CardHeader>
            <CardTitle>项目 A</CardTitle>
            <CardDescription>进行中</CardDescription>
          </CardHeader>
          <CardContent>
            <p>这是项目 A 的描述信息。</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>项目 B</CardTitle>
            <CardDescription>已完成</CardDescription>
          </CardHeader>
          <CardContent>
            <p>这是项目 B 的描述信息。</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>项目 C</CardTitle>
            <CardDescription>待开始</CardDescription>
          </CardHeader>
          <CardContent>
            <p>这是项目 C 的描述信息。</p>
          </CardContent>
        </Card>
      </div>
    `,
  }),
};
