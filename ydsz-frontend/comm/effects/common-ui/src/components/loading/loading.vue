<!--
 * loading 通用组件
 *
 * @path comm\effects\common-ui\src\components\loading\loading.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { YDSZLoading } from '@ydsz-core/shadcn-ui';
import { cn } from '@ydsz-core/shared/utils';

interface LoadingProps {
  class?: string;
  /**
   * @zh_CN 最小加载时间
   * @en_US Minimum loading time
   */
  minLoadingTime?: number;

  /**
   * @zh_CN loading状态开启
   */
  spinning?: boolean;
  /**
   * @zh_CN 文字
   */
  text?: string;
}

defineOptions({ name: 'Loading' });
const props = defineProps<LoadingProps>();
</script>
<template>
  <div :class="cn('relative min-h-20', props.class)">
    <slot></slot>
    <YDSZLoading
      :min-loading-time="props.minLoadingTime"
      :spinning="props.spinning"
      :text="props.text"
    >
      <template v-if="$slots.icon" #icon>
        <slot name="icon"></slot>
      </template>
    </YDSZLoading>
  </div>
</template>
