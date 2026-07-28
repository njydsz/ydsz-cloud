<!--
 * 文件节点（表单组件）
 *
 * @path apps\nextwiki-web\src\views\file\file-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 文件节点（表单组件）
 * <p>文件/文件夹的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { FileApi } from '#/api/file';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createFileApi, updateFileApi } from '#/api/file';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  fileName: '',
  parentId: '',
});
const rules = {
  fileName: [{ required: true, message: '请输入文件名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: FileApi.FileVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        fileName: data.record.fileName || '',
        parentId: data.record.parentId || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  fileName: '',
  parentId: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateFileApi(formData as any); ElMessage.success('更新成功'); }
      else { await createFileApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑文件管理' : '新增文件管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="文件名称" prop="fileName">
        <ElInput v-model="formData.fileName" placeholder="请输入文件名称" />
      </ElFormItem>
      <ElFormItem label="父目录ID" prop="parentId">
        <ElInput v-model="formData.parentId" placeholder="请输入父目录ID" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
