<!--
 * FormMessage Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\form\FormMessage.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { toValue } from 'vue';

import { ErrorMessage } from 'vee-validate';

import { useFormField } from './useFormField';

const { formMessageId, name } = useFormField();
</script>

<template>
  <ErrorMessage
    :id="formMessageId"
    :name="toValue(name)"
    as="p"
    class="text-destructive text-[0.8rem]"
  />
</template>
