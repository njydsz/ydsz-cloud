<!--
 * 系统变量编辑表单组件
 *
 * @path apps\literule-web\src\views\variable\variable-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
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
const formData = reactive({ id: '',
  variableName: '',
  variableType: '',
  defaultValue: '',
  description: '',
  status: 0,
});
const rules = {
  variableName: [{ required: true, message: '请输入变量名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: VariableApi.VariableVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        variableName: data.record.variableName || '',
        variableType: data.record.variableType || '',
        defaultValue: data.record.defaultValue || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  variableName: '',
  variableType: '',
  defaultValue: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateVariableApi(formData as any); ElMessage.success('更新成功'); }
      else { await createVariableApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑规则变量' : '新增规则变量'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="变量名称" prop="variableName">
        <ElInput v-model="formData.variableName" placeholder="请输入变量名称" />
      </ElFormItem>
      <ElFormItem label="变量类型" prop="variableType">
        <ElInput v-model="formData.variableType" placeholder="请输入变量类型" />
      </ElFormItem>
      <ElFormItem label="默认值" prop="defaultValue">
        <ElInput v-model="formData.defaultValue" placeholder="请输入默认值" />
      </ElFormItem>
      <ElFormItem label="描述">
        <ElInput v-model="formData.description" type="textarea" :rows="2" placeholder="请输入描述" />
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
