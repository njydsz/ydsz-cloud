<script lang="ts" setup>
import type { RateCardApi } from '#/api/rateCard';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createRateCardApi, updateRateCardApi } from '#/api/rateCard';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  rateName: '',
  roleLevel: '',
  standardRate: 0,
  overtimeRate: 0,
  currency: '',
  effectiveDate: '',
  status: 0,
});
const rules = {
  rateName: [{ required: true, message: '请输入费率名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RateCardApi.RateCardVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        rateName: data.record.rateName || '',
        roleLevel: data.record.roleLevel || '',
        standardRate: data.record.standardRate || 0,
        overtimeRate: data.record.overtimeRate || 0,
        currency: data.record.currency || '',
        effectiveDate: data.record.effectiveDate || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  rateName: '',
  roleLevel: '',
  standardRate: 0,
  overtimeRate: 0,
  currency: '',
  effectiveDate: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateRateCardApi(formData as any); ElMessage.success('更新成功'); }
      else { await createRateCardApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑费率卡管理' : '新增费率卡管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="费率名称" prop="rateName">
        <ElInput v-model="formData.rateName" placeholder="请输入费率名称" />
      </ElFormItem>
      <ElFormItem label="角色等级" prop="roleLevel">
        <ElInput v-model="formData.roleLevel" placeholder="请输入角色等级" />
      </ElFormItem>
      <ElFormItem label="标准费率">
        <ElInputNumber v-model="formData.standardRate" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="加班费率">
        <ElInputNumber v-model="formData.overtimeRate" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="币种" prop="currency">
        <ElInput v-model="formData.currency" placeholder="请输入币种" />
      </ElFormItem>
      <ElFormItem label="生效日期" prop="effectiveDate">
        <ElInput v-model="formData.effectiveDate" placeholder="请输入生效日期" />
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
