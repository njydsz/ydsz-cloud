<script lang="ts" setup>
/**
 * 文件评论（表单组件）
 * <p>评论的创建表单，支持 Markdown、@ 提及。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CommentApi } from '#/api/comment';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createCommentApi, updateCommentApi } from '#/api/comment';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  fileId: '',
  content: '',
});
const rules = {
  fileId: [{ required: true, message: '请输入文件ID', trigger: 'blur' }],
  content: [{ required: true, message: '请输入评论内容', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: CommentApi.CommentVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        fileId: data.record.fileId || '',
        content: data.record.content || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  fileId: '',
  content: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateCommentApi(formData as any); ElMessage.success('更新成功'); }
      else { await createCommentApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑文件评论' : '新增文件评论'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="文件ID" prop="fileId">
        <ElInput v-model="formData.fileId" placeholder="请输入文件ID" />
      </ElFormItem>
      <ElFormItem label="评论内容">
        <ElInput v-model="formData.content" type="textarea" :rows="2" placeholder="请输入评论内容" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
