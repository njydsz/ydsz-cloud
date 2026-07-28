<!--
 * 任务连接器（表单组件）
 *
 * @path apps\cronjob-web\src\views\connector\connector-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 任务连接器（表单组件）
 * <p>执行器的注册/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ConnectorApi } from '#/api/connector';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createConnectorApi, updateConnectorApi } from '#/api/connector';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  connectorName: '',
  connectorType: '',
  endpoint: '',
  authType: '',
  status: 0,
});
const rules = {
  connectorName: [{ required: true, message: '请输入连接器名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ConnectorApi.ConnectorVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        connectorName: data.record.connectorName || '',
        connectorType: data.record.connectorType || '',
        endpoint: data.record.endpoint || '',
        authType: data.record.authType || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  connectorName: '',
  connectorType: '',
  endpoint: '',
  authType: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateConnectorApi(formData as any); ElMessage.success('更新成功'); }
      else { await createConnectorApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑连接器管理' : '新增连接器管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="连接器名称" prop="connectorName">
        <ElInput v-model="formData.connectorName" placeholder="请输入连接器名称" />
      </ElFormItem>
      <ElFormItem label="类型" prop="connectorType">
        <ElInput v-model="formData.connectorType" placeholder="请输入类型" />
      </ElFormItem>
      <ElFormItem label="端点" prop="endpoint">
        <ElInput v-model="formData.endpoint" placeholder="请输入端点" />
      </ElFormItem>
      <ElFormItem label="认证类型" prop="authType">
        <ElInput v-model="formData.authType" placeholder="请输入认证类型" />
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
