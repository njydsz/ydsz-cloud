<script lang="ts" setup>
/**
 * 销售商机（表单组件）
 * <p>商机的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { OpportunityApi } from '#/api/opportunity';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createOpportunityApi, updateOpportunityApi } from '#/api/opportunity';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  opportunityName: '',
  customerName: '',
  opportunityType: '',
  estimatedAmount: 0,
  stage: '',
  expectedCloseDate: '',
  salesPerson: '',
  status: 0,
});
const rules = {
  opportunityName: [{ required: true, message: '请输入商机名称', trigger: 'blur' }],
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: OpportunityApi.OpportunityVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        opportunityName: data.record.opportunityName || '',
        customerName: data.record.customerName || '',
        opportunityType: data.record.opportunityType || '',
        estimatedAmount: data.record.estimatedAmount || 0,
        stage: data.record.stage || '',
        expectedCloseDate: data.record.expectedCloseDate || '',
        salesPerson: data.record.salesPerson || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  opportunityName: '',
  customerName: '',
  opportunityType: '',
  estimatedAmount: 0,
  stage: '',
  expectedCloseDate: '',
  salesPerson: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateOpportunityApi(formData as any); ElMessage.success('更新成功'); }
      else { await createOpportunityApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑商机管理' : '新增商机管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="商机名称" prop="opportunityName">
        <ElInput v-model="formData.opportunityName" placeholder="请输入商机名称" />
      </ElFormItem>
      <ElFormItem label="客户名称" prop="customerName">
        <ElInput v-model="formData.customerName" placeholder="请输入客户名称" />
      </ElFormItem>
      <ElFormItem label="商机类型" prop="opportunityType">
        <ElInput v-model="formData.opportunityType" placeholder="请输入商机类型" />
      </ElFormItem>
      <ElFormItem label="预计金额">
        <ElInputNumber v-model="formData.estimatedAmount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="商机阶段" prop="stage">
        <ElInput v-model="formData.stage" placeholder="请输入商机阶段" />
      </ElFormItem>
      <ElFormItem label="预计关闭日期" prop="expectedCloseDate">
        <ElInput v-model="formData.expectedCloseDate" placeholder="请输入预计关闭日期" />
      </ElFormItem>
      <ElFormItem label="销售人员" prop="salesPerson">
        <ElInput v-model="formData.salesPerson" placeholder="请输入销售人员" />
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
