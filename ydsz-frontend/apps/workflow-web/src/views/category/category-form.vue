<!--
 * 流程分类（表单组件）
 *
 * @path apps\workflow-web\src\views\category\category-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程分类（表单组件）
 * <p>流程分类的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CategoryApi } from '#/api/category';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createCategoryApi, updateCategoryApi } from '#/api/category';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  categoryCode: '',
  categoryName: '',
  sort: 0,
  status: 0,
});
const rules = {
  categoryCode: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: CategoryApi.CategoryVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        categoryCode: data.record.categoryCode || '',
        categoryName: data.record.categoryName || '',
        sort: data.record.sort || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  categoryCode: '',
  categoryName: '',
  sort: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateCategoryApi(formData as any); ElMessage.success('更新成功'); }
      else { await createCategoryApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑流程分类' : '新增流程分类'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="分类编码" prop="categoryCode">
        <ElInput v-model="formData.categoryCode" placeholder="请输入分类编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="分类名称" prop="categoryName">
        <ElInput v-model="formData.categoryName" placeholder="请输入分类名称" />
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
