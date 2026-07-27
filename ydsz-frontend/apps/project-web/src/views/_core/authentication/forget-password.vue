<script lang="ts" setup>
import type { YDSZFormSchema } from '@ydsz/common-ui';
import type { Recordable } from '@ydsz/types';

import { computed, ref } from 'vue';

import { AuthenticationForgetPassword, z } from '@ydsz/common-ui';
import { $t } from '@ydsz/locales';

defineOptions({ name: 'ForgetPassword' });

const loading = ref(false);

const formSchema = computed((): YDSZFormSchema[] => {
  return [
    {
      component: 'YDSZInput',
      componentProps: {
        placeholder: 'example@example.com',
      },
      fieldName: 'email',
      label: $t('authentication.email'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.emailTip') })
        .email($t('authentication.emailValidErrorTip')),
    },
  ];
});

function handleSubmit(value: Recordable<any>) {
  // eslint-disable-next-line no-console
  console.log('reset email:', value);
}
</script>

<template>
  <AuthenticationForgetPassword
    :form-schema="formSchema"
    :loading="loading"
    @submit="handleSubmit"
  />
</template>
