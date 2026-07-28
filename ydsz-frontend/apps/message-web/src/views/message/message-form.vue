<!--
 * message-form 表单页面组件
 *
 * @path apps\message-web\src\views\message\message-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 消息（详情组件）
 * <p>消息详情的展示组件，包含发送渠道、收件人、主题、内容、回执状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { MessageApi } from '#/api/message';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createMessageApi, updateMessageApi } from '#/api/message';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  channel: '',
  recipient: '',
  subject: '',
  content: '',
});
const rules = {
  channel: [{ required: true, message: '请输入通道', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: MessageApi.MessageVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        channel: data.record.channel || '',
        recipient: data.record.recipient || '',
        subject: data.record.subject || '',
        content: data.record.content || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  channel: '',
  recipient: '',
  subject: '',
  content: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateMessageApi(formData as any); ElMessage.success('更新成功'); }
      else { await createMessageApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑消息管理' : '新增消息管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="通道" prop="channel">
        <ElInput v-model="formData.channel" placeholder="请输入通道" />
      </ElFormItem>
      <ElFormItem label="接收者" prop="recipient">
        <ElInput v-model="formData.recipient" placeholder="请输入接收者" />
      </ElFormItem>
      <ElFormItem label="主题" prop="subject">
        <ElInput v-model="formData.subject" placeholder="请输入主题" />
      </ElFormItem>
      <ElFormItem label="内容">
        <ElInput v-model="formData.content" type="textarea" :rows="2" placeholder="请输入内容" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
