<script lang="ts" setup>
import type { BreakpointApi } from '#/api/breakpoint';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createBreakpointApi, updateBreakpointApi } from '#/api/breakpoint';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  ruleCode: '',
  condition: '',
  status: 0,
});
const rules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: BreakpointApi.BreakpointVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        ruleCode: data.record.ruleCode || '',
        condition: data.record.condition || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  ruleCode: '',
  condition: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateBreakpointApi(formData as any); ElMessage.success('更新成功'); }
      else { await createBreakpointApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑断点调试' : '新增断点调试'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="规则编码" prop="ruleCode">
        <ElInput v-model="formData.ruleCode" placeholder="请输入规则编码" />
      </ElFormItem>
      <ElFormItem label="断点条件">
        <ElInput v-model="formData.condition" type="textarea" :rows="2" placeholder="请输入断点条件" />
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
