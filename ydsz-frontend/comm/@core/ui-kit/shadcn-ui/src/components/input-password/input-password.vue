<!--
 * input-password 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\input-password\input-password.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { ref, useSlots } from 'vue';

import { Eye, EyeOff } from '@ydsz-core/icons';
import { cn } from '@ydsz-core/shared/utils';

import { Input } from '../../ui';
import PasswordStrength from './password-strength.vue';

interface Props {
  class?: any;
  /**
   * 是否显示密码强度
   */
  passwordStrength?: boolean;
}

defineOptions({
  inheritAttrs: false,
});

const props = defineProps<Props>();

const modelValue = defineModel<string>();

const slots = useSlots();

const show = ref(false);
</script>

<template>
  <div class="relative w-full">
    <Input
      v-bind="$attrs"
      v-model="modelValue"
      :class="cn(props.class)"
      :type="show ? 'text' : 'password'"
      :aria-label="$attrs.placeholder || '密码输入框'"
    />
    <template v-if="passwordStrength">
      <PasswordStrength :password="modelValue" aria-label="密码强度指示器" />
      <p v-if="slots.strengthText" class="text-muted-foreground mt-1.5 text-xs" aria-live="polite">
        <slot name="strengthText"> </slot>
      </p>
    </template>
    <div
      :class="{
        'top-3': !!passwordStrength,
        'top-1/2 -translate-y-1/2 items-center': !passwordStrength,
      }"
      class="hover:text-foreground text-foreground/60 absolute inset-y-0 right-0 flex cursor-pointer pr-3 text-lg leading-5"
      role="button"
      :aria-label="show ? '隐藏密码' : '显示密码'"
      :aria-pressed="show"
      tabindex="0"
      @click="show = !show"
      @keydown.enter="show = !show"
      @keydown.space="show = !show"
    >
      <Eye v-if="show" class="size-4" aria-hidden="true" />
      <EyeOff v-else class="size-4" aria-hidden="true" />
    </div>
  </div>
</template>
