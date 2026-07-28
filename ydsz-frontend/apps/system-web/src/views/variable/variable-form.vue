<script lang="ts" setup>
/**
 * 系统变量（表单组件）
 * <p>系统变量的编辑表单，支持加密存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VariableApi } from '#/api/variable';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createVariableApi, updateVariableApi } from '#/api/variable';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  variableKey: '',
  variableValue: '',
  variableType: '',
  remark: '',
  status: 1,
});

const rules = {
  variableKey: [{ required: true, message: '请输入变量键', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: VariableApi.VariableVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        variableKey: data.record.variableKey || '',
        variableValue: data.record.variableValue || '',
        variableType: data.record.variableType || '',
        remark: data.record.remark || '',
        status: data.record.status || 1,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        variableKey: '',
        variableValue: '',
        variableType: '',
        remark: '',
        status: 1,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateVariableApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createVariableApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑系统' : '新增系统'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="变量键" prop="variableKey">
        <ElInput v-model="formData.variableKey" placeholder="请输入变量键" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="变量类型" prop="variableType">
        <ElInput v-model="formData.variableType" placeholder="请输入变量类型" />
      </ElFormItem>

      <ElFormItem label="变量值">
        <ElInput v-model="formData.variableValue" type="textarea" :rows="2" placeholder="请输入变量值" />
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </ElFormItem>
      <ElFormItem label="状态">
        <ElRadioGroup v-model="formData.status">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
