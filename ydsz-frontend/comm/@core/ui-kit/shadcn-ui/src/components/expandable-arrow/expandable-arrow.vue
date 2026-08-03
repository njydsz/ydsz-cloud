<!--
 * expandable-arrow 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\expandable-arrow\expandable-arrow.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { ChevronDown } from '@ydsz-core/icons';
import { cn } from '@ydsz-core/shared/utils';

const props = defineProps<{
  class?: string;
}>();

// 控制箭头展开/收起状态
const collapsed = defineModel({ default: false });
</script>

<template>
  <div
    :class="cn('ydsz-link inline-flex cursor-pointer items-center', props.class)"
    role="button"
    tabindex="0"
    :aria-expanded="!collapsed"
    @click="collapsed = !collapsed"
    @keydown.enter="collapsed = !collapsed"
    @keydown.space.prevent="collapsed = !collapsed"
  >
    <slot :is-expanded="collapsed">
      {{ collapsed }}
    </slot>
    <div
      :class="{ 'rotate-180': !collapsed }"
      class="transition-transform duration-300"
      aria-hidden="true"
    >
      <slot name="icon">
        <ChevronDown class="size-4" />
      </slot>
    </div>
  </div>
</template>
