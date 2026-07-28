<!--
 * execution-form 表单页面组件
 *
 * @path apps\project-web\src\views\execution\execution-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目执行（表单组件）
 * <p>项目执行的录入表单，包含工时、里程碑、风险。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ExecutionApi } from '#/api/execution';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createExecutionApi, updateExecutionApi } from '#/api/execution';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  taskName: '',
  assignee: '',
  plannedStart: '',
  plannedEnd: '',
  actualStart: '',
  actualEnd: '',
  progress: 0,
  status: 0,
});
const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ExecutionApi.ExecutionVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        taskName: data.record.taskName || '',
        assignee: data.record.assignee || '',
        plannedStart: data.record.plannedStart || '',
        plannedEnd: data.record.plannedEnd || '',
        actualStart: data.record.actualStart || '',
        actualEnd: data.record.actualEnd || '',
        progress: data.record.progress || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  taskName: '',
  assignee: '',
  plannedStart: '',
  plannedEnd: '',
  actualStart: '',
  actualEnd: '',
  progress: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateExecutionApi(formData as any); ElMessage.success('更新成功'); }
      else { await createExecutionApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑执行管理' : '新增执行管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="任务名称" prop="taskName">
        <ElInput v-model="formData.taskName" placeholder="请输入任务名称" />
      </ElFormItem>
      <ElFormItem label="负责人" prop="assignee">
        <ElInput v-model="formData.assignee" placeholder="请输入负责人" />
      </ElFormItem>
      <ElFormItem label="计划开始" prop="plannedStart">
        <ElInput v-model="formData.plannedStart" placeholder="请输入计划开始" />
      </ElFormItem>
      <ElFormItem label="计划结束" prop="plannedEnd">
        <ElInput v-model="formData.plannedEnd" placeholder="请输入计划结束" />
      </ElFormItem>
      <ElFormItem label="实际开始" prop="actualStart">
        <ElInput v-model="formData.actualStart" placeholder="请输入实际开始" />
      </ElFormItem>
      <ElFormItem label="实际结束" prop="actualEnd">
        <ElInput v-model="formData.actualEnd" placeholder="请输入实际结束" />
      </ElFormItem>
      <ElFormItem label="进度">
        <ElInputNumber v-model="formData.progress" :min="0" :max="999" />
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
