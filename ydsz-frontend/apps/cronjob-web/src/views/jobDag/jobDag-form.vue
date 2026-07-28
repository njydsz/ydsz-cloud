<script lang="ts" setup>
/**
 * 任务 DAG（表单组件）
 * <p>任务 DAG 的可视化编辑表单，支持节点拖拽、连线配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { JobDagApi } from '#/api/jobDag';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createJobDagApi, updateJobDagApi } from '#/api/jobDag';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  dagName: '',
  dagCode: '',
  description: '',
  status: 0,
});
const rules = {
  dagName: [{ required: true, message: '请输入DAG名称', trigger: 'blur' }],
  dagCode: [{ required: true, message: '请输入DAG编码', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: JobDagApi.JobDagVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        dagName: data.record.dagName || '',
        dagCode: data.record.dagCode || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  dagName: '',
  dagCode: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateJobDagApi(formData as any); ElMessage.success('更新成功'); }
      else { await createJobDagApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑DAG管理' : '新增DAG管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="DAG名称" prop="dagName">
        <ElInput v-model="formData.dagName" placeholder="请输入DAG名称" />
      </ElFormItem>
      <ElFormItem label="DAG编码" prop="dagCode">
        <ElInput v-model="formData.dagCode" placeholder="请输入DAG编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="描述">
        <ElInput v-model="formData.description" type="textarea" :rows="2" placeholder="请输入描述" />
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
