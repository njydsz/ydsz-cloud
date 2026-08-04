<!--
 * dict-select 通用组件
 *
 * @path comm\effects\shared-business\src\components\dict-select.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 字典选择器组件 — 从全局字典缓存获取数据
 */
import { computed, onMounted, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { useDictStore } from '../store/dict';

interface Props {
  /** 字典类型编码，如 system_status */
  dictType: string;
  /** 数据权限过滤（仅显示指定值） */
  values?: string[];
  modelValue?: string | number | (string | number)[];
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  multiple?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请选择',
  disabled: false,
  clearable: true,
  multiple: false,
  values: undefined,
});

const emit = defineEmits<{
  'update:modelValue': [value: string | number | (string | number)[]];
  change: [value: string | number];
}>();

const dictStore = useDictStore();

/** 从缓存获取字典项 */
const dictItems = computed(() => dictStore.getItems(props.dictType));

const options = computed(() => {
  const items = dictItems.value;
  if (props.values?.length) {
    return items.filter((item) => props.values!.includes(item.value));
  }
  return items;
});

/** 保证字典加载 */
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

function handleChange(value: string | number) {
  emit('update:modelValue', value);
  emit('change', value);
}
</script>

<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :multiple="multiple"
    :loading="dictStore.loadingTypes.has(dictType)"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="opt in options"
      :key="opt.value"
      :label="opt.label"
      :value="opt.value"
    />
  </el-select>
</template>
