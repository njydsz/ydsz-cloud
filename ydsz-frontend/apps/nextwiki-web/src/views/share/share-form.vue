<!--
 * share-form 表单页面组件
 *
 * @path apps\nextwiki-web\src\views\share\share-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 文件分享（表单组件）
 * <p>分享链接的创建表单，支持公开/密码/有效期。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ShareApi } from '#/api/share';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createShareApi, updateShareApi } from '#/api/share';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  fileId: '',
  shareTo: '',
  permission: '',
  expireDate: '',
});
const rules = {
  fileId: [{ required: true, message: '请输入文件ID', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ShareApi.ShareVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        fileId: data.record.fileId || '',
        shareTo: data.record.shareTo || '',
        permission: data.record.permission || '',
        expireDate: data.record.expireDate || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  fileId: '',
  shareTo: '',
  permission: '',
  expireDate: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateShareApi(formData as any); ElMessage.success('更新成功'); }
      else { await createShareApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑文件分享' : '新增文件分享'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="文件ID" prop="fileId">
        <ElInput v-model="formData.fileId" placeholder="请输入文件ID" />
      </ElFormItem>
      <ElFormItem label="分享给" prop="shareTo">
        <ElInput v-model="formData.shareTo" placeholder="请输入分享给" />
      </ElFormItem>
      <ElFormItem label="权限" prop="permission">
        <ElInput v-model="formData.permission" placeholder="请输入权限" />
      </ElFormItem>
      <ElFormItem label="过期日期" prop="expireDate">
        <ElInput v-model="formData.expireDate" placeholder="请输入过期日期" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
