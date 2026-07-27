<script lang="ts" setup>
import type { InvoiceApi } from '#/api/invoice';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createInvoiceApi, updateInvoiceApi } from '#/api/invoice';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  invoiceCode: '',
  customerName: '',
  invoiceAmount: 0,
  invoiceDate: '',
  invoiceType: '',
  status: 0,
});
const rules = {
  invoiceCode: [{ required: true, message: '请输入发票编号', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: InvoiceApi.InvoiceVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        invoiceCode: data.record.invoiceCode || '',
        customerName: data.record.customerName || '',
        invoiceAmount: data.record.invoiceAmount || 0,
        invoiceDate: data.record.invoiceDate || '',
        invoiceType: data.record.invoiceType || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  invoiceCode: '',
  customerName: '',
  invoiceAmount: 0,
  invoiceDate: '',
  invoiceType: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateInvoiceApi(formData as any); ElMessage.success('更新成功'); }
      else { await createInvoiceApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑发票管理' : '新增发票管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="发票编号" prop="invoiceCode">
        <ElInput v-model="formData.invoiceCode" placeholder="请输入发票编号" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="客户名称" prop="customerName">
        <ElInput v-model="formData.customerName" placeholder="请输入客户名称" />
      </ElFormItem>
      <ElFormItem label="发票金额">
        <ElInputNumber v-model="formData.invoiceAmount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="开票日期" prop="invoiceDate">
        <ElInput v-model="formData.invoiceDate" placeholder="请输入开票日期" />
      </ElFormItem>
      <ElFormItem label="发票类型" prop="invoiceType">
        <ElInput v-model="formData.invoiceType" placeholder="请输入发票类型" />
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
