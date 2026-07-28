<!--
 * payment-form 表单页面组件
 *
 * @path apps\project-web\src\views\payment\payment-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目付款（表单组件）
 * <p>付款申请的录入表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PaymentApi } from '#/api/payment';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createPaymentApi, updatePaymentApi } from '#/api/payment';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  paymentAmount: 0,
  paymentDate: '',
  paymentMethod: '',
  description: '',
  status: 0,
});
const rules = {
  paymentAmount: [{ required: true, message: '请输入回款金额', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: PaymentApi.PaymentVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        paymentAmount: data.record.paymentAmount || 0,
        paymentDate: data.record.paymentDate || '',
        paymentMethod: data.record.paymentMethod || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  paymentAmount: 0,
  paymentDate: '',
  paymentMethod: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updatePaymentApi(formData as any); ElMessage.success('更新成功'); }
      else { await createPaymentApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑回款管理' : '新增回款管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="回款金额">
        <ElInputNumber v-model="formData.paymentAmount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="回款日期" prop="paymentDate">
        <ElInput v-model="formData.paymentDate" placeholder="请输入回款日期" />
      </ElFormItem>
      <ElFormItem label="回款方式" prop="paymentMethod">
        <ElInput v-model="formData.paymentMethod" placeholder="请输入回款方式" />
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
