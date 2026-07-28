<!--
 * login 页面组件
 *
 * @path main\src\views\_core\authentication\login.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { YDSZFormSchema } from '@ydsz/common-ui';
import type { BasicOption } from '@ydsz/types';

import { computed, markRaw } from 'vue';

import { AuthenticationLogin, SliderCaptcha, z } from '@ydsz/common-ui';
import { $t } from '@ydsz/locales';

import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

const MOCK_TENANT_OPTIONS: BasicOption[] = [
  {
    label: '瑞米软件',
    value: 'ydsz',
  },
  {
    label: '测试租户 A',
    value: 'tenant-a',
  },
  {
    label: '测试租户 B',
    value: 'tenant-b',
  },
];

const formSchema = computed((): YDSZFormSchema[] => {
  return [
    {
      component: 'YDSZSelect',
      componentProps: {
        options: MOCK_TENANT_OPTIONS,
        placeholder: '请选择租户',
      },
      fieldName: 'tenant',
      label: '租户',
      rules: z
        .string()
        .min(1, { message: '请选择租户' })
        .optional()
        .default('ydsz'),
    },
    {
      component: 'YDSZInput',
      componentProps: {
        placeholder: '请输入账号',
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: '请输入账号' }),
    },
    {
      component: 'YDSZInputPassword',
      componentProps: {
        placeholder: '请输入密码',
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: '请输入密码' }),
    },
    {
      component: markRaw(SliderCaptcha),
      fieldName: 'captcha',
      rules: z.boolean().refine((value) => value, {
        message: $t('authentication.verifyRequiredTip'),
      }),
    },
  ];
});
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    @submit="authStore.authLogin"
  />
</template>
