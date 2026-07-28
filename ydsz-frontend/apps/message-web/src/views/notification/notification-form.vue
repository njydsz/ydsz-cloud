<!--
 * notification-form 表单页面组件
 *
 * @path apps\message-web\src\views\notification\notification-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 站内通知（详情组件）
 * <p>站内通知的详情展示，支持标记已读。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { NotificationApi } from '#/api/notification';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createNotificationApi, updateNotificationApi } from '#/api/notification';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  title: '',
  content: '',
  type: '',
});
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: NotificationApi.NotificationVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        title: data.record.title || '',
        content: data.record.content || '',
        type: data.record.type || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  title: '',
  content: '',
  type: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateNotificationApi(formData as any); ElMessage.success('更新成功'); }
      else { await createNotificationApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑站内通知' : '新增站内通知'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="标题" prop="title">
        <ElInput v-model="formData.title" placeholder="请输入标题" />
      </ElFormItem>
      <ElFormItem label="内容">
        <ElInput v-model="formData.content" type="textarea" :rows="2" placeholder="请输入内容" />
      </ElFormItem>
      <ElFormItem label="类型" prop="type">
        <ElInput v-model="formData.type" placeholder="请输入类型" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
