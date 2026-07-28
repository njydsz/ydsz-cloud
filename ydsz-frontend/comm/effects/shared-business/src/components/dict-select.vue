<script lang="ts" setup>
/**
 * 字典选择器组件 — 从 system 模块获取字典数据
 */
import { ref, watch } from 'vue';
import { ElSelect, ElOption } from 'element-plus';

interface Props {
  dictType: string;
  modelValue?: string;
  placeholder?: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请选择',
  disabled: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const options = ref<{ label: string; value: string }[]>([]);

// TODO: 从 system 模块 API 获取字典数据
// const { getAllDictItemsApi } = await import('@ydsz/shared-auth');
// watch(() => props.dictType, async (type) => {
//   options.value = await getAllDictItemsApi(type);
// }, { immediate: true });
</script>

<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-option
      v-for="opt in options"
      :key="opt.value"
      :label="opt.label"
      :value="opt.value"
    />
  </el-select>
</template>
