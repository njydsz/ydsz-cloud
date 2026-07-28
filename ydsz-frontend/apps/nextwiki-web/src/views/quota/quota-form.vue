<script lang="ts" setup>
/**
 * 文件配额（表单组件）
 * <p>配额的编辑表单，限制总容量/单文件大小/文件数。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { QuotaApi } from '#/api/quota';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createQuotaApi, updateQuotaApi } from '#/api/quota';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  userId: '',
  totalQuota: 0,
});
const rules = {
  userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
  totalQuota: [{ required: true, message: '请输入总配额(MB)', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: QuotaApi.QuotaVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        userId: data.record.userId || '',
        totalQuota: data.record.totalQuota || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  userId: '',
  totalQuota: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateQuotaApi(formData as any); ElMessage.success('更新成功'); }
      else { await createQuotaApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑存储配额' : '新增存储配额'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="用户ID" prop="userId">
        <ElInput v-model="formData.userId" placeholder="请输入用户ID" />
      </ElFormItem>
      <ElFormItem label="总配额(MB)">
        <ElInputNumber v-model="formData.totalQuota" :min="0" :max="999" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
