<!--
 * app-tour 通用组件 — 用户操作引导（onboarding tour）
 *
 * @path comm\effects\shared-business\src\components\app-tour.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 引导组件 — 挂载后按需启动，提供声明式配置
 */
import { onMounted } from 'vue';

import {
  useAppTour,
  type TourStep,
} from '../composables/use-app-tour';

interface Props {
  /** 引导唯一标识 */
  tourId: string;
  /** 步骤列表 */
  steps: TourStep[];
  /** 是否强制展示，默认 false */
  force?: boolean;
  /** 是否挂载后自动启动，默认 true */
  autoStart?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  force: false,
  autoStart: true,
});

const emit = defineEmits<{
  finished: [];
  skipped: [];
}>();

const tour = useAppTour({
  id: props.tourId,
  steps: props.steps,
  force: props.force,
});

onMounted(() => {
  if (props.autoStart) {
    // 等元素渲染完成后启动
    setTimeout(() => {
      if (tour.start()) {
        // 监听完成/跳过
        const origFinish = tour.finish;
        const origSkip = tour.skip;
        tour.finish = () => {
          origFinish();
          emit('finished');
        };
        tour.skip = () => {
          origSkip();
          emit('skipped');
        };
      }
    }, 300);
  }
});

defineExpose({
  isCompleted: tour.isCompleted,
  start: tour.start,
  skip: tour.skip,
  finish: tour.finish,
});
</script>

<template>
  <slot />
</template>
