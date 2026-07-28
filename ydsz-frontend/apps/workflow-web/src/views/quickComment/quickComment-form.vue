<!--
 * 快捷回复（表单组件）
 *
 * @path apps\workflow-web\src\views\quickComment\quickComment-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 快捷回复（表单组件）
 * <p>快捷回复模板的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { QuickCommentApi } from '#/api/quickComment';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createQuickCommentApi, updateQuickCommentApi } from '#/api/quickComment';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  content: '',
  category: '',
  sort: 0,
  status: 0,
});
const rules = {
  content: [{ required: true, message: '请输入评语内容', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: QuickCommentApi.QuickCommentVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        content: data.record.content || '',
        category: data.record.category || '',
        sort: data.record.sort || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  content: '',
  category: '',
  sort: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateQuickCommentApi(formData as any); ElMessage.success('更新成功'); }
      else { await createQuickCommentApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑快捷评语' : '新增快捷评语'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="评语内容">
        <ElInput v-model="formData.content" type="textarea" :rows="2" placeholder="请输入评语内容" />
      </ElFormItem>
      <ElFormItem label="分类" prop="category">
        <ElInput v-model="formData.category" placeholder="请输入分类" />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="formData.sort" :min="0" :max="999" />
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
