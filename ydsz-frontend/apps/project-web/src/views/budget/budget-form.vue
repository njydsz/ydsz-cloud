<!--
 * budget-form 表单页面组件
 *
 * @path apps\project-web\src\views\budget\budget-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目预算（表单组件）
 * <p>预算的编制/调整表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { BudgetApi } from '#/api/budget';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createBudgetApi, updateBudgetApi } from '#/api/budget';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  budgetItemName: '',
  budgetType: '',
  plannedAmount: 0,
  actualAmount: 0,
  status: 0,
});
const rules = {
  budgetItemName: [{ required: true, message: '请输入预算项名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: BudgetApi.BudgetVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        budgetItemName: data.record.budgetItemName || '',
        budgetType: data.record.budgetType || '',
        plannedAmount: data.record.plannedAmount || 0,
        actualAmount: data.record.actualAmount || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  budgetItemName: '',
  budgetType: '',
  plannedAmount: 0,
  actualAmount: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateBudgetApi(formData as any); ElMessage.success('更新成功'); }
      else { await createBudgetApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑预算管理' : '新增预算管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="预算项名称" prop="budgetItemName">
        <ElInput v-model="formData.budgetItemName" placeholder="请输入预算项名称" />
      </ElFormItem>
      <ElFormItem label="预算类型" prop="budgetType">
        <ElInput v-model="formData.budgetType" placeholder="请输入预算类型" />
      </ElFormItem>
      <ElFormItem label="计划金额">
        <ElInputNumber v-model="formData.plannedAmount" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="实际金额">
        <ElInputNumber v-model="formData.actualAmount" :min="0" :max="999" />
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
