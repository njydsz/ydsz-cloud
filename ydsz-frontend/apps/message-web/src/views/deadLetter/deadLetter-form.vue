<script lang="ts" setup>
/**
 * 死信队列（详情组件）
 * <p>死信消息的详情展示，支持重投/丢弃操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DeadLetterApi } from '#/api/deadLetter';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createDeadLetterApi, updateDeadLetterApi } from '#/api/deadLetter';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  messageId: '',
  channel: '',
  errorMessage: '',
});
const rules = {
  messageId: [{ required: true, message: '请输入消息ID', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DeadLetterApi.DeadLetterVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        messageId: data.record.messageId || '',
        channel: data.record.channel || '',
        errorMessage: data.record.errorMessage || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  messageId: '',
  channel: '',
  errorMessage: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateDeadLetterApi(formData as any); ElMessage.success('更新成功'); }
      else { await createDeadLetterApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑死信队列' : '新增死信队列'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="消息ID" prop="messageId">
        <ElInput v-model="formData.messageId" placeholder="请输入消息ID" />
      </ElFormItem>
      <ElFormItem label="通道" prop="channel">
        <ElInput v-model="formData.channel" placeholder="请输入通道" />
      </ElFormItem>
      <ElFormItem label="错误信息">
        <ElInput v-model="formData.errorMessage" type="textarea" :rows="2" placeholder="请输入错误信息" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
