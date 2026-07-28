<script lang="ts" setup>
/**
 * 流程模板（表单组件）
 * <p>流程模板的创建/编辑表单，支持 BPMN 2.0 可视化设计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  channel: '',
  subject: '',
  content: '',
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
        channel: data.record.channel || '',
        subject: data.record.subject || '',
        content: data.record.content || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  templateCode: '',
  templateName: '',
  channel: '',
  subject: '',
  content: '',
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
const title = computed(() => (isEdit.value ? '编辑模板管理' : '新增模板管理'));
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
      <ElFormItem label="通道" prop="channel">
        <ElInput v-model="formData.channel" placeholder="请输入通道" />
      </ElFormItem>
      <ElFormItem label="主题" prop="subject">
        <ElInput v-model="formData.subject" placeholder="请输入主题" />
      </ElFormItem>
      <ElFormItem label="内容">
        <ElInput v-model="formData.content" type="textarea" :rows="2" placeholder="请输入内容" />
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
