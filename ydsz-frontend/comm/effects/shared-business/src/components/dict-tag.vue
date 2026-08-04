<!--
 * dict-tag 通用组件
 *
 * @path comm\effects\shared-business\src\components\dict-tag.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 字典标签组件 — 将字典值渲染为带颜色的标签（表格列常用）
 */
import { computed, onMounted, watch } from 'vue';

import { ElTag } from 'element-plus';

import { useDictStore } from '@ydsz/stores';

interface Props {
  /** 字典类型编码 */
  dictType: string;
  /** 字典值 */
  value?: string | number;
  /** 自定义颜色映射（value → el-tag type），不传时按索引轮询 */
  colorMap?: Record<string, 'primary' | 'success' | 'info' | 'warning' | 'danger'>;
}

const props = withDefaults(defineProps<Props>(), {
  value: '',
  colorMap: undefined,
});

const dictStore = useDictStore();

const colorPalette: ('primary' | 'success' | 'info' | 'warning' | 'danger')[] = [
  'primary',
  'success',
  'info',
  'warning',
  'danger',
];

const items = computed(() => dictStore.getItems(props.dictType));

const current = computed(() =>
  items.value.find((item) => String(item.itemValue) === String(props.value)),
);

const tagType = computed(() => {
  if (props.colorMap?.[String(props.value)]) {
    return props.colorMap[String(props.value)];
  }
  if (!props.value && props.value !== 0) {
    return 'info';
  }
  // 按字典项排序位置轮询取色，保证同一值颜色稳定
  const idx = items.value.findIndex(
    (item) => String(item.itemValue) === String(props.value),
  );
  return colorPalette[idx % colorPalette.length] ?? 'info';
});

onMounted(() => {
  dictStore.ensureLoaded(props.dictType);
});

watch(
  () => props.dictType,
  (type) => {
    dictStore.ensureLoaded(type);
  },
  { immediate: true },
);
</script>

<template>
  <el-tag v-if="current" :type="tagType" size="small" effect="light">
    {{ current.itemText }}
  </el-tag>
  <span v-else class="dict-tag--empty">-</span>
</template>

<style scoped>
.dict-tag--empty {
  color: #909399;
}
</style>
