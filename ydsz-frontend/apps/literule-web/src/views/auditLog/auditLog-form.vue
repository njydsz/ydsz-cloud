<script lang="ts" setup>
/**
 * 规则审计日志（详情组件）
 * <p>规则审计日志的详情展示。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { AuditLogApi } from '#/api/auditLog';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createAuditLogApi, updateAuditLogApi } from '#/api/auditLog';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  ruleCode: '',
});
const rules = {
  // 无必填项
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: AuditLogApi.AuditLogVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        ruleCode: data.record.ruleCode || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  ruleCode: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateAuditLogApi(formData as any); ElMessage.success('更新成功'); }
      else { await createAuditLogApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑审计日志' : '新增审计日志'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="规则编码" prop="ruleCode">
        <ElInput v-model="formData.ruleCode" placeholder="请输入规则编码" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
