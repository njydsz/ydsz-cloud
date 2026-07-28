<!--
 * 文件标签（表单组件）
 *
 * @path apps\nextwiki-web\src\views\tag\tag-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 文件标签（表单组件）
 * <p>标签的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { TagApi } from '#/api/tag';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createTagApi, updateTagApi } from '#/api/tag';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  tagName: '',
  tagColor: '',
});
const rules = {
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: TagApi.TagVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        tagName: data.record.tagName || '',
        tagColor: data.record.tagColor || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  tagName: '',
  tagColor: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateTagApi(formData as any); ElMessage.success('更新成功'); }
      else { await createTagApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑标签管理' : '新增标签管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="标签名称" prop="tagName">
        <ElInput v-model="formData.tagName" placeholder="请输入标签名称" />
      </ElFormItem>
      <ElFormItem label="标签颜色" prop="tagColor">
        <ElInput v-model="formData.tagColor" placeholder="请输入标签颜色" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
