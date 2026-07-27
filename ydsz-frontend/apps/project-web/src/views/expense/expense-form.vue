<script lang="ts" setup>
import type { ExpenseApi } from '#/api/expense';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createExpenseApi, updateExpenseApi } from '#/api/expense';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  expenseType: '',
  amount: 0,
  expenseDate: '',
  applicant: '',
  description: '',
  status: 0,
});
const rules = {
  expenseType: [{ required: true, message: '请输入费用类型', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ExpenseApi.ExpenseVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        expenseType: data.record.expenseType || '',
        amount: data.record.amount || 0,
        expenseDate: data.record.expenseDate || '',
        applicant: data.record.applicant || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  expenseType: '',
  amount: 0,
  expenseDate: '',
  applicant: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateExpenseApi(formData as any); ElMessage.success('更新成功'); }
      else { await createExpenseApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑费用管理' : '新增费用管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="费用类型" prop="expenseType">
        <ElInput v-model="formData.expenseType" placeholder="请输入费用类型" />
      </ElFormItem>
      <ElFormItem label="金额">
        <ElInputNumber v-model="formData.amount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="费用日期" prop="expenseDate">
        <ElInput v-model="formData.expenseDate" placeholder="请输入费用日期" />
      </ElFormItem>
      <ElFormItem label="申请人" prop="applicant">
        <ElInput v-model="formData.applicant" placeholder="请输入申请人" />
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
