<!--
 * EVM 挣值管理（表单组件）
 *
 * @path apps\project-web\src\views\evm\evm-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * EVM 挣值管理（详情组件）
 * <p>EVM 指标的详情展示，支持趋势图、健康度评分。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { EvmApi } from '#/api/evm';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createEvmApi, updateEvmApi } from '#/api/evm';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  measureDate: '',
  pv: 0,
  ev: 0,
  ac: 0,
});
const rules = {
  measureDate: [{ required: true, message: '请输入测量日期', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: EvmApi.EvmVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        measureDate: data.record.measureDate || '',
        pv: data.record.pv || 0,
        ev: data.record.ev || 0,
        ac: data.record.ac || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  measureDate: '',
  pv: 0,
  ev: 0,
  ac: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateEvmApi(formData as any); ElMessage.success('更新成功'); }
      else { await createEvmApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑EVM 挣值管理' : '新增EVM 挣值管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="测量日期" prop="measureDate">
        <ElInput v-model="formData.measureDate" placeholder="请输入测量日期" />
      </ElFormItem>
      <ElFormItem label="PV计划值">
        <ElInputNumber v-model="formData.pv" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="EV挣值">
        <ElInputNumber v-model="formData.ev" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="AC实际成本">
        <ElInputNumber v-model="formData.ac" :min="0" :max="999" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
