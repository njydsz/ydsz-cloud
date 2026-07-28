<!--
 * FormControl Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\form\FormControl.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { Slot } from 'radix-vue';

import { useFormField } from './useFormField';

const { error, formDescriptionId, formItemId, formMessageId } = useFormField();
</script>

<template>
  <Slot
    :id="formItemId"
    :aria-describedby="
      !error ? `${formDescriptionId}` : `${formDescriptionId} ${formMessageId}`
    "
    :aria-invalid="!!error"
  >
    <slot></slot>
  </Slot>
</template>
