<script lang="ts" setup>
import type { SlaApi } from '#/api/sla';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createSlaApi, updateSlaApi } from '#/api/sla';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  slaName: '',
  templateId: '',
  maxDuration: 0,
  warnThreshold: 0,
  status: 0,
});
const rules = {
  slaName: [{ required: true, message: '请输入SLA名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: SlaApi.SlaVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        slaName: data.record.slaName || '',
        templateId: data.record.templateId || '',
        maxDuration: data.record.maxDuration || 0,
        warnThreshold: data.record.warnThreshold || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  slaName: '',
  templateId: '',
  maxDuration: 0,
  warnThreshold: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateSlaApi(formData as any); ElMessage.success('更新成功'); }
      else { await createSlaApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑SLA管理' : '新增SLA管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="SLA名称" prop="slaName">
        <ElInput v-model="formData.slaName" placeholder="请输入SLA名称" />
      </ElFormItem>
      <ElFormItem label="模板ID" prop="templateId">
        <ElInput v-model="formData.templateId" placeholder="请输入模板ID" />
      </ElFormItem>
      <ElFormItem label="最大时长(分钟)">
        <ElInputNumber v-model="formData.maxDuration" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="预警阈值(百分比)">
        <ElInputNumber v-model="formData.warnThreshold" :min="0" :max="999" />
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
