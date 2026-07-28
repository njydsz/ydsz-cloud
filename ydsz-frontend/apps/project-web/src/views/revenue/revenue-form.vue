<!--
 * revenue-form 表单页面组件
 *
 * @path apps\project-web\src\views\revenue\revenue-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目回款（表单组件）
 * <p>回款记录的录入表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RevenueApi } from '#/api/revenue';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createRevenueApi, updateRevenueApi } from '#/api/revenue';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  revenueType: '',
  amount: 0,
  revenueDate: '',
  description: '',
  status: 0,
});
const rules = {
  revenueType: [{ required: true, message: '请输入收入类型', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RevenueApi.RevenueVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        revenueType: data.record.revenueType || '',
        amount: data.record.amount || 0,
        revenueDate: data.record.revenueDate || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  revenueType: '',
  amount: 0,
  revenueDate: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateRevenueApi(formData as any); ElMessage.success('更新成功'); }
      else { await createRevenueApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑收入管理' : '新增收入管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="收入类型" prop="revenueType">
        <ElInput v-model="formData.revenueType" placeholder="请输入收入类型" />
      </ElFormItem>
      <ElFormItem label="金额">
        <ElInputNumber v-model="formData.amount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="收入日期" prop="revenueDate">
        <ElInput v-model="formData.revenueDate" placeholder="请输入收入日期" />
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
