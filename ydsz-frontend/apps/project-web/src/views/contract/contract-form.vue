<script lang="ts" setup>
import type { ContractApi } from '#/api/contract';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createContractApi, updateContractApi } from '#/api/contract';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  contractCode: '',
  contractName: '',
  customerName: '',
  contractAmount: 0,
  contractType: '',
  signDate: '',
  startDate: '',
  endDate: '',
  status: 0,
});
const rules = {
  contractCode: [{ required: true, message: '请输入合同编号', trigger: 'blur' }],
  contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ContractApi.ContractVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        contractCode: data.record.contractCode || '',
        contractName: data.record.contractName || '',
        customerName: data.record.customerName || '',
        contractAmount: data.record.contractAmount || 0,
        contractType: data.record.contractType || '',
        signDate: data.record.signDate || '',
        startDate: data.record.startDate || '',
        endDate: data.record.endDate || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  contractCode: '',
  contractName: '',
  customerName: '',
  contractAmount: 0,
  contractType: '',
  signDate: '',
  startDate: '',
  endDate: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateContractApi(formData as any); ElMessage.success('更新成功'); }
      else { await createContractApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑合同管理' : '新增合同管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="合同编号" prop="contractCode">
        <ElInput v-model="formData.contractCode" placeholder="请输入合同编号" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="合同名称" prop="contractName">
        <ElInput v-model="formData.contractName" placeholder="请输入合同名称" />
      </ElFormItem>
      <ElFormItem label="客户名称" prop="customerName">
        <ElInput v-model="formData.customerName" placeholder="请输入客户名称" />
      </ElFormItem>
      <ElFormItem label="合同金额">
        <ElInputNumber v-model="formData.contractAmount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="合同类型" prop="contractType">
        <ElInput v-model="formData.contractType" placeholder="请输入合同类型" />
      </ElFormItem>
      <ElFormItem label="签订日期" prop="signDate">
        <ElInput v-model="formData.signDate" placeholder="请输入签订日期" />
      </ElFormItem>
      <ElFormItem label="开始日期" prop="startDate">
        <ElInput v-model="formData.startDate" placeholder="请输入开始日期" />
      </ElFormItem>
      <ElFormItem label="结束日期" prop="endDate">
        <ElInput v-model="formData.endDate" placeholder="请输入结束日期" />
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
