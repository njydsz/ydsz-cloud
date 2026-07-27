<script lang="ts" setup>
import type { TemplateApi } from '#/api/template';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createTemplateApi, updateTemplateApi } from '#/api/template';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  templateCode: '',
  templateName: '',
  category: '',
  description: '',
  status: 0,
});
const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: TemplateApi.TemplateVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        templateCode: data.record.templateCode || '',
        templateName: data.record.templateName || '',
        category: data.record.category || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  templateCode: '',
  templateName: '',
  category: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateTemplateApi(formData as any); ElMessage.success('更新成功'); }
      else { await createTemplateApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑流程模板' : '新增流程模板'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="模板编码" prop="templateCode">
        <ElInput v-model="formData.templateCode" placeholder="请输入模板编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="模板名称" prop="templateName">
        <ElInput v-model="formData.templateName" placeholder="请输入模板名称" />
      </ElFormItem>
      <ElFormItem label="分类" prop="category">
        <ElInput v-model="formData.category" placeholder="请输入分类" />
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
