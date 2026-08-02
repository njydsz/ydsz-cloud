<!--
 * approval-form 表单页面组件
 *
 * @path apps\agent-web\src\views\approval\approval-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * Agent 审批（表单组件）
 * <p>审批单的查看/审批表单，展示工具调用详情、参数、风险等级。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ApprovalApi } from '#/api/approval';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createApprovalApi, updateApprovalApi } from '#/api/approval';
/** 表单提交成功后触发，通知父级列表页刷新数据 */
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  agentId: '',
  requestType: '',
  requestContent: '',
  approver: '',
});
const rules = {
  agentId: [{ required: true, message: '请输入Agent ID', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ApprovalApi.ApprovalVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        agentId: data.record.agentId || '',
        requestType: data.record.requestType || '',
        requestContent: data.record.requestContent || '',
        approver: data.record.approver || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  agentId: '',
  requestType: '',
  requestContent: '',
  approver: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateApprovalApi(formData as any); ElMessage.success('更新成功'); }
      else { await createApprovalApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑人工审批' : '新增人工审批'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="Agent ID" prop="agentId">
        <ElInput v-model="formData.agentId" placeholder="请输入Agent ID" />
      </ElFormItem>
      <ElFormItem label="请求类型" prop="requestType">
        <ElInput v-model="formData.requestType" placeholder="请输入请求类型" />
      </ElFormItem>
      <ElFormItem label="请求内容">
        <ElInput v-model="formData.requestContent" type="textarea" :rows="2" placeholder="请输入请求内容" />
      </ElFormItem>
      <ElFormItem label="审批人" prop="approver">
        <ElInput v-model="formData.approver" placeholder="请输入审批人" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
