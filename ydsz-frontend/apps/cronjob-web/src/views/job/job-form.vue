<!--
 * job-form 表单页面组件
 *
 * @path apps\cronjob-web\src\views\job\job-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 定时任务（表单组件）
 * <p>定时任务的创建/编辑表单，包含 Cron 表达式、负责人、告警通道、超时配置、并发策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { JobApi } from '#/api/job';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createJobApi, updateJobApi } from '#/api/job';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  jobName: '',
  jobGroup: '',
  cronExpression: '',
  jobType: '',
  executorHandler: '',
  executorParam: '',
  status: 0,
});
const rules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: JobApi.JobVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        jobName: data.record.jobName || '',
        jobGroup: data.record.jobGroup || '',
        cronExpression: data.record.cronExpression || '',
        jobType: data.record.jobType || '',
        executorHandler: data.record.executorHandler || '',
        executorParam: data.record.executorParam || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  jobName: '',
  jobGroup: '',
  cronExpression: '',
  jobType: '',
  executorHandler: '',
  executorParam: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateJobApi(formData as any); ElMessage.success('更新成功'); }
      else { await createJobApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑任务管理' : '新增任务管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="任务名称" prop="jobName">
        <ElInput v-model="formData.jobName" placeholder="请输入任务名称" />
      </ElFormItem>
      <ElFormItem label="任务分组" prop="jobGroup">
        <ElInput v-model="formData.jobGroup" placeholder="请输入任务分组" />
      </ElFormItem>
      <ElFormItem label="Cron表达式" prop="cronExpression">
        <ElInput v-model="formData.cronExpression" placeholder="请输入Cron表达式" />
      </ElFormItem>
      <ElFormItem label="任务类型" prop="jobType">
        <ElInput v-model="formData.jobType" placeholder="请输入任务类型" />
      </ElFormItem>
      <ElFormItem label="执行器" prop="executorHandler">
        <ElInput v-model="formData.executorHandler" placeholder="请输入执行器" />
      </ElFormItem>
      <ElFormItem label="执行参数" prop="executorParam">
        <ElInput v-model="formData.executorParam" placeholder="请输入执行参数" />
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
