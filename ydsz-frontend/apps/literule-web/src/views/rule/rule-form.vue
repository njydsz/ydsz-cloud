<script lang="ts" setup>
/**
 * 规则定义（表单组件）
 * <p>规则定义的创建/编辑表单，包含规则类型选择、版本管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RuleApi } from '#/api/rule';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createRuleApi, updateRuleApi } from '#/api/rule';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  ruleCode: '',
  ruleName: '',
  ruleType: '',
  priority: 0,
  description: '',
  status: 0,
});
const rules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RuleApi.RuleVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        ruleCode: data.record.ruleCode || '',
        ruleName: data.record.ruleName || '',
        ruleType: data.record.ruleType || '',
        priority: data.record.priority || 0,
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  ruleCode: '',
  ruleName: '',
  ruleType: '',
  priority: 0,
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateRuleApi(formData as any); ElMessage.success('更新成功'); }
      else { await createRuleApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑规则管理' : '新增规则管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="规则编码" prop="ruleCode">
        <ElInput v-model="formData.ruleCode" placeholder="请输入规则编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="规则名称" prop="ruleName">
        <ElInput v-model="formData.ruleName" placeholder="请输入规则名称" />
      </ElFormItem>
      <ElFormItem label="规则类型" prop="ruleType">
        <ElInput v-model="formData.ruleType" placeholder="请输入规则类型" />
      </ElFormItem>
      <ElFormItem label="优先级">
        <ElInputNumber v-model="formData.priority" :min="0" :max="999" />
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
