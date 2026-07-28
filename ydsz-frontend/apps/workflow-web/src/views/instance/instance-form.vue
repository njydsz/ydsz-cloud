<!--
 * 流程实例（表单组件）
 *
 * @path apps\workflow-web\src\views\instance\instance-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程实例（详情组件）
 * <p>流程实例的详情展示，包含流程图、流转历史。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { InstanceApi } from '#/api/instance';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createInstanceApi, updateInstanceApi } from '#/api/instance';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  templateId: '',
});
const rules = {
  // 无必填项
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: InstanceApi.InstanceVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        templateId: data.record.templateId || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  templateId: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateInstanceApi(formData as any); ElMessage.success('更新成功'); }
      else { await createInstanceApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑流程实例' : '新增流程实例'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="模板ID" prop="templateId">
        <ElInput v-model="formData.templateId" placeholder="请输入模板ID" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
