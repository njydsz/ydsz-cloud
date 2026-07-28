<!--
 * batch-form 表单页面组件
 *
 * @path apps\message-web\src\views\batch\batch-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 消息批量发送（表单组件）
 * <p>批量发送的创建表单，支持收件人 CSV 上传、发送策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { BatchApi } from '#/api/batch';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createBatchApi, updateBatchApi } from '#/api/batch';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  batchName: '',
  channel: '',
});
const rules = {
  batchName: [{ required: true, message: '请输入批次名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: BatchApi.BatchVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        batchName: data.record.batchName || '',
        channel: data.record.channel || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  batchName: '',
  channel: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateBatchApi(formData as any); ElMessage.success('更新成功'); }
      else { await createBatchApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑批量发送' : '新增批量发送'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="批次名称" prop="batchName">
        <ElInput v-model="formData.batchName" placeholder="请输入批次名称" />
      </ElFormItem>
      <ElFormItem label="通道" prop="channel">
        <ElInput v-model="formData.channel" placeholder="请输入通道" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
