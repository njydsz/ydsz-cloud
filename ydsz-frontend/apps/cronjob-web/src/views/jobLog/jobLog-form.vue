<!--
 * 任务执行日志（详情组件）
 *
 * @path apps\cronjob-web\src\views\jobLog\jobLog-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 任务执行日志（详情组件）
 * <p>任务执行日志的详情展示，包含堆栈、参数、返回值的完整内容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { JobLogApi } from '#/api/jobLog';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createJobLogApi, updateJobLogApi } from '#/api/jobLog';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  jobName: '',
});
const rules = {
  // 无必填项
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: JobLogApi.JobLogVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        jobName: data.record.jobName || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  jobName: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateJobLogApi(formData as any); ElMessage.success('更新成功'); }
      else { await createJobLogApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑执行日志' : '新增执行日志'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="任务名称" prop="jobName">
        <ElInput v-model="formData.jobName" placeholder="请输入任务名称" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
