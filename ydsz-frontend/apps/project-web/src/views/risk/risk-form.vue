<!--
 * risk-form 表单页面组件
 *
 * @path apps\project-web\src\views\risk\risk-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目风险（表单组件）
 * <p>风险的登记表单，支持概率/影响矩阵。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RiskApi } from '#/api/risk';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createRiskApi, updateRiskApi } from '#/api/risk';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  riskName: '',
  riskType: '',
  probability: 0,
  impact: 0,
  mitigation: '',
  status: 0,
});
const rules = {
  riskName: [{ required: true, message: '请输入风险名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RiskApi.RiskVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        riskName: data.record.riskName || '',
        riskType: data.record.riskType || '',
        probability: data.record.probability || 0,
        impact: data.record.impact || 0,
        mitigation: data.record.mitigation || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  riskName: '',
  riskType: '',
  probability: 0,
  impact: 0,
  mitigation: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateRiskApi(formData as any); ElMessage.success('更新成功'); }
      else { await createRiskApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑风险管理' : '新增风险管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="风险名称" prop="riskName">
        <ElInput v-model="formData.riskName" placeholder="请输入风险名称" />
      </ElFormItem>
      <ElFormItem label="风险类型" prop="riskType">
        <ElInput v-model="formData.riskType" placeholder="请输入风险类型" />
      </ElFormItem>
      <ElFormItem label="概率">
        <ElInputNumber v-model="formData.probability" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="影响">
        <ElInputNumber v-model="formData.impact" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="缓解措施">
        <ElInput v-model="formData.mitigation" type="textarea" :rows="2" placeholder="请输入缓解措施" />
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
