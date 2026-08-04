<!--
 * virtual-select 通用组件 — 大数据量下拉选择器
 *
 * @path comm\effects\shared-business\src\components\virtual-select.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 虚拟滚动下拉选择器 — 基于 @tanstack/vue-virtual
 *
 * 适用于选项 > 1000 条的 select 场景（如部门/用户/字典超大数据集）。
 * 若数据量小，直接使用 element-plus 原生 el-select 即可。
 */
import { computed, ref } from 'vue';

import { ElSelectV2 } from 'element-plus';

interface Option {
  label: string;
  value: string | number;
  [key: string]: any;
}

interface Props {
  /** 选项数据（大数据场景推荐传入全部选项） */
  options: Option[];
  modelValue?: string | number | (string | number)[];
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  multiple?: boolean;
  filterable?: boolean;
  /** 虚拟滚动的可见行数，默认 10 */
  virtualRows?: number;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请选择',
  disabled: false,
  clearable: true,
  multiple: false,
  filterable: true,
  virtualRows: 10,
});

const emit = defineEmits<{
  'update:modelValue': [value: string | number | (string | number)[]];
}>();

/** 过滤后的选项 */
const filteredOptions = ref<Option[]>(props.options);

// 搜索关键字
const searchKeyword = ref('');

const visibleOptions = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (!keyword) return filteredOptions.value;
  return filteredOptions.value.filter(
    (opt) =>
      opt.label.toLowerCase().includes(keyword) ||
      String(opt.value).toLowerCase().includes(keyword),
  );
});

function handleSearch(keyword: string) {
  searchKeyword.value = keyword;
}

function handleUpdate(value: string | number | (string | number)[]) {
  emit('update:modelValue', value);
}

// 同步外部 options 变化
import { watch } from 'vue';
watch(
  () => props.options,
  (val) => {
    filteredOptions.value = val;
  },
  { immediate: true },
);
</script>

<template>
  <el-select-v2
    :model-value="modelValue"
    :options="visibleOptions"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :multiple="multiple"
    :filterable="filterable"
    :remote="false"
    :automatic-dropdown="true"
    @update:model-value="handleUpdate"
    @search="handleSearch"
  />
</template>
