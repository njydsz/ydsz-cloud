/**
 * Dialog 组件 Storybook Stories
 *
 * P1-2.3: 组件文档化 — Dialog 组件交互式文档
 *
 * @path comm/@core/ui-kit/shadcn-ui/src/ui/dialog/Dialog.stories.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Meta, StoryObj } from '@storybook/vue3';

import { ref } from 'vue';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './index';

const meta: Meta = {
  title: 'Core/Dialog',
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: '对话框组件用于在不离开当前页面的情况下向用户展示重要信息或收集输入。',
      },
    },
  },
};

export default meta;
type Story = StoryObj;

/** 基础对话框 */
export const Default: Story = {
  render: () => ({
    components: { Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter },
    setup() {
      const open = ref(false);
      return { open };
    },
    template: `
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <button style="padding: 8px 16px; border: none; border-radius: 4px; background: #007bff; color: white; cursor: pointer;">
            打开对话框
          </button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认操作</DialogTitle>
            <DialogDescription>
              此操作将永久删除该项目，是否继续？
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <button @click="open = false" style="padding: 8px 16px; border: 1px solid #ccc; border-radius: 4px; background: white; cursor: pointer;">
              取消
            </button>
            <button @click="open = false" style="padding: 8px 16px; border: none; border-radius: 4px; background: #dc3545; color: white; cursor: pointer;">
              确认删除
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    `,
  }),
};

/** 表单对话框 */
export const FormDialog: Story = {
  render: () => ({
    components: { Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter },
    setup() {
      const open = ref(false);
      return { open };
    },
    template: `
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <button style="padding: 8px 16px; border: none; border-radius: 4px; background: #007bff; color: white; cursor: pointer;">
            编辑用户
          </button>
        </DialogTrigger>
        <DialogContent style="max-width: 450px;">
          <DialogHeader>
            <DialogTitle>编辑用户信息</DialogTitle>
            <DialogDescription>
              修改用户的个人资料信息。
            </DialogDescription>
          </DialogHeader>
          <div style="display: flex; flex-direction: column; gap: 16px; padding: 16px 0;">
            <div>
              <label style="display: block; margin-bottom: 4px; font-weight: 500;">姓名</label>
              <input type="text" value="张三" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" />
            </div>
            <div>
              <label style="display: block; margin-bottom: 4px; font-weight: 500;">邮箱</label>
              <input type="email" value="zhangsan@example.com" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" />
            </div>
          </div>
          <DialogFooter>
            <button @click="open = false" style="padding: 8px 16px; border: 1px solid #ccc; border-radius: 4px; background: white; cursor: pointer;">
              取消
            </button>
            <button @click="open = false" style="padding: 8px 16px; border: none; border-radius: 4px; background: #007bff; color: white; cursor: pointer;">
              保存
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    `,
  }),
};

/** 信息展示对话框 */
export const InfoDialog: Story = {
  render: () => ({
    components: { Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogDescription },
    setup() {
      const open = ref(false);
      return { open };
    },
    template: `
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <button style="padding: 8px 16px; border: none; border-radius: 4px; background: #007bff; color: white; cursor: pointer;">
            查看详情
          </button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>系统通知</DialogTitle>
            <DialogDescription>
              系统将于今晚 22:00 进行维护升级，预计持续 2 小时。请提前保存工作内容。
            </DialogDescription>
          </DialogHeader>
          <div style="padding: 16px 0;">
            <p style="margin-bottom: 8px;"><strong>维护时间：</strong>2024-01-15 22:00 - 2024-01-16 00:00</p>
            <p style="margin-bottom: 8px;"><strong>影响范围：</strong>所有在线服务</p>
            <p><strong>注意事项：</strong>请提前保存未提交的数据</p>
          </div>
          <DialogFooter>
            <button @click="open = false" style="padding: 8px 16px; border: none; border-radius: 4px; background: #007bff; color: white; cursor: pointer;">
              我知道了
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    `,
  }),
};
