/**
 * Form 组件 Storybook Stories
 *
 * P1-2.3: 组件文档化 — Form 组件交互式文档
 *
 * @path comm/@core/ui-kit/shadcn-ui/src/ui/form/Form.stories.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Meta, StoryObj } from '@storybook/vue3';

import { ref } from 'vue';

import { Button } from '../button';
import { Input } from '../input';
import { Label } from '../label';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from './index';

const meta: Meta = {
  title: 'Core/Form',
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: '表单组件用于收集用户输入，提供字段验证、错误提示和表单布局管理。',
      },
    },
  },
};

export default meta;
type Story = StoryObj;

/** 基础登录表单 */
export const LoginForm: Story = {
  render: () => ({
    components: { Form, FormField, FormItem, FormLabel, FormControl, FormMessage, Input, Button },
    setup() {
      const form = ref({
        username: '',
        password: '',
      });

      const handleSubmit = () => {
        console.log('提交数据:', form.value);
      };

      return { form, handleSubmit };
    },
    template: `
      <Form @submit.prevent="handleSubmit" style="width: 400px;">
        <FormField v-slot="{ field }" name="username">
          <FormItem>
            <FormLabel>用户名</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.username" placeholder="请输入用户名" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ field }" name="password">
          <FormItem>
            <FormLabel>密码</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.password" type="password" placeholder="请输入密码" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <Button type="submit" style="width: 100%; margin-top: 16px;">登录</Button>
      </Form>
    `,
  }),
};

/** 注册表单（带验证） */
export const RegistrationForm: Story = {
  render: () => ({
    components: { Form, FormField, FormItem, FormLabel, FormControl, FormDescription, FormMessage, Input, Button },
    setup() {
      const form = ref({
        email: '',
        username: '',
        password: '',
        confirmPassword: '',
      });

      const handleSubmit = () => {
        console.log('注册数据:', form.value);
      };

      return { form, handleSubmit };
    },
    template: `
      <Form @submit.prevent="handleSubmit" style="width: 450px;">
        <FormField v-slot="{ field }" name="email">
          <FormItem>
            <FormLabel>邮箱</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.email" type="email" placeholder="your@email.com" />
            </FormControl>
            <FormDescription>我们将向此邮箱发送验证链接</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ field }" name="username">
          <FormItem>
            <FormLabel>用户名</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.username" placeholder="请输入用户名" />
            </FormControl>
            <FormDescription>3-20 个字符，只能包含字母、数字和下划线</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ field }" name="password">
          <FormItem>
            <FormLabel>密码</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.password" type="password" placeholder="请输入密码" />
            </FormControl>
            <FormDescription>至少 8 个字符，包含大小写字母和数字</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ field }" name="confirmPassword">
          <FormItem>
            <FormLabel>确认密码</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.confirmPassword" type="password" placeholder="再次输入密码" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <Button type="submit" style="width: 100%; margin-top: 16px;">注册</Button>
      </Form>
    `,
  }),
};

/** 搜索表单（内联布局） */
export const SearchForm: Story = {
  render: () => ({
    components: { Form, FormField, FormItem, FormControl, Input, Button },
    setup() {
      const form = ref({
        keyword: '',
      });

      const handleSubmit = () => {
        console.log('搜索:', form.value.keyword);
      };

      return { form, handleSubmit };
    },
    template: `
      <Form @submit.prevent="handleSubmit" style="display: flex; gap: 12px; align-items: flex-end;">
        <FormField v-slot="{ field }" name="keyword" style="flex: 1;">
          <FormItem>
            <FormControl>
              <Input v-bind="field" v-model="form.keyword" placeholder="搜索关键词..." />
            </FormControl>
          </FormItem>
        </FormField>

        <Button type="submit">搜索</Button>
      </Form>
    `,
  }),
};

/** 带错误状态的表单 */
export const FormWithErrors: Story = {
  render: () => ({
    components: { Form, FormField, FormItem, FormLabel, FormControl, FormMessage, Input, Button },
    setup() {
      const form = ref({
        email: 'invalid-email',
        password: '123',
      });

      const handleSubmit = () => {
        console.log('提交数据:', form.value);
      };

      return { form, handleSubmit };
    },
    template: `
      <Form @submit.prevent="handleSubmit" style="width: 400px;">
        <FormField v-slot="{ field }" name="email" :error="'请输入有效的邮箱地址'">
          <FormItem>
            <FormLabel>邮箱</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.email" type="email" />
            </FormControl>
            <FormMessage>请输入有效的邮箱地址</FormMessage>
          </FormItem>
        </FormField>

        <FormField v-slot="{ field }" name="password" :error="'密码长度不足'">
          <FormItem>
            <FormLabel>密码</FormLabel>
            <FormControl>
              <Input v-bind="field" v-model="form.password" type="password" />
            </FormControl>
            <FormMessage>密码长度不足</FormMessage>
          </FormItem>
        </FormField>

        <Button type="submit" style="width: 100%; margin-top: 16px;">提交</Button>
      </Form>
    `,
  }),
};
