<!--
 * config-form 表单页面组件
 *
 * @path apps\system-web\src\views\config\config-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 系统配置（表单组件）
 * <p>系统参数的编辑表单，支持热更新。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ConfigApi } from '#/api/config';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createConfigApi, updateConfigApi } from '#/api/config';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  configKey: '',
  configName: '',
  configGroup: '',
  valueType: '',
  configValue: '',
  remark: '',
  isPublic: 0,
  status: 1,
});

const rules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: ConfigApi.ConfigVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        configKey: data.record.configKey || '',
        configName: data.record.configName || '',
        configGroup: data.record.configGroup || '',
        valueType: data.record.valueType || '',
        configValue: data.record.configValue || '',
        remark: data.record.remark || '',
        isPublic: data.record.isPublic || 0,
        status: data.record.status || 1,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        configKey: '',
        configName: '',
        configGroup: '',
        valueType: '',
        configValue: '',
        remark: '',
        isPublic: 0,
        status: 1,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateConfigApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createConfigApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑系统' : '新增系统'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="配置键" prop="configKey">
        <ElInput v-model="formData.configKey" placeholder="请输入配置键" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="配置名称" prop="configName">
        <ElInput v-model="formData.configName" placeholder="请输入配置名称" />
      </ElFormItem>
      <ElFormItem label="配置分组" prop="configGroup">
        <ElInput v-model="formData.configGroup" placeholder="请输入配置分组" />
      </ElFormItem>
      <ElFormItem label="值类型" prop="valueType">
        <ElInput v-model="formData.valueType" placeholder="请输入值类型" />
      </ElFormItem>

      <ElFormItem label="配置值">
        <ElInput v-model="formData.configValue" type="textarea" :rows="2" placeholder="请输入配置值" />
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </ElFormItem>
      <ElFormItem label="是否公开">
        <ElRadioGroup v-model="formData.isPublic">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
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
