<!--
 * task-form 表单页面组件
 *
 * @path apps\workflow-web\src\views\task\task-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程任务（详情组件）
 * <p>流程任务的详情展示，支持审批、转办、驳回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { TaskApi } from '#/api/task';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createTaskApi, updateTaskApi } from '#/api/task';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  taskName: '',
});
const rules = {
  // 无必填项
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: TaskApi.TaskVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        taskName: data.record.taskName || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  taskName: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateTaskApi(formData as any); ElMessage.success('更新成功'); }
      else { await createTaskApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑待办任务' : '新增待办任务'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="任务名称" prop="taskName">
        <ElInput v-model="formData.taskName" placeholder="请输入任务名称" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
