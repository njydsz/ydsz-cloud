<!--
 * analysis-chart-card Vue 组件
 *
 * @path comm\effects\common-ui\src\ui\dashboard\analysis\analysis-chart-card.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '@ydsz-core/shadcn-ui';

interface Props {
  title: string;
}

defineOptions({
  name: 'AnalysisChartCard',
});

withDefaults(defineProps<Props>(), {});
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle class="text-xl">{{ title }}</CardTitle>
    </CardHeader>
    <CardContent>
      <slot></slot>
    </CardContent>
  </Card>
</template>
