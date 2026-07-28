<script lang="ts" setup>
/**
 * 任务告警（表单组件）
 * <p>告警规则的编辑表单，支持邮件/短信/企微/钉钉多渠道。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { AlertApi } from '#/api/alert';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createAlertApi, updateAlertApi } from '#/api/alert';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  alertName: '',
  alertType: '',
  alertLevel: '',
  condition: '',
  notifyChannels: '',
  status: 0,
});
const rules = {
  alertName: [{ required: true, message: '请输入告警名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: AlertApi.AlertVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        alertName: data.record.alertName || '',
        alertType: data.record.alertType || '',
        alertLevel: data.record.alertLevel || '',
        condition: data.record.condition || '',
        notifyChannels: data.record.notifyChannels || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  alertName: '',
  alertType: '',
  alertLevel: '',
  condition: '',
  notifyChannels: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateAlertApi(formData as any); ElMessage.success('更新成功'); }
      else { await createAlertApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑告警管理' : '新增告警管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="告警名称" prop="alertName">
        <ElInput v-model="formData.alertName" placeholder="请输入告警名称" />
      </ElFormItem>
      <ElFormItem label="告警类型" prop="alertType">
        <ElInput v-model="formData.alertType" placeholder="请输入告警类型" />
      </ElFormItem>
      <ElFormItem label="告警级别" prop="alertLevel">
        <ElInput v-model="formData.alertLevel" placeholder="请输入告警级别" />
      </ElFormItem>
      <ElFormItem label="条件">
        <ElInput v-model="formData.condition" type="textarea" :rows="2" placeholder="请输入条件" />
      </ElFormItem>
      <ElFormItem label="通知通道" prop="notifyChannels">
        <ElInput v-model="formData.notifyChannels" placeholder="请输入通知通道" />
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
