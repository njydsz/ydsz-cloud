<script lang="ts" setup>
/**
 * 应用（表单组件）
 * <p>应用的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { AppApi } from '#/api/app';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createAppApi, updateAppApi } from '#/api/app';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  appCode: '',
  appName: '',
  appSecret: '',
  appType: '',
  redirectUri: '',
  remark: '',
  status: 1,
});

const rules = {
  appCode: [{ required: true, message: '请输入应用编码', trigger: 'blur' }],
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: AppApi.AppVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        appCode: data.record.appCode || '',
        appName: data.record.appName || '',
        appSecret: data.record.appSecret || '',
        appType: data.record.appType || '',
        redirectUri: data.record.redirectUri || '',
        remark: data.record.remark || '',
        status: data.record.status || 1,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        appCode: '',
        appName: '',
        appSecret: '',
        appType: '',
        redirectUri: '',
        remark: '',
        status: 1,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateAppApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createAppApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑应用' : '新增应用'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="应用编码" prop="appCode">
        <ElInput v-model="formData.appCode" placeholder="请输入应用编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="应用名称" prop="appName">
        <ElInput v-model="formData.appName" placeholder="请输入应用名称" />
      </ElFormItem>
      <ElFormItem label="应用密钥" prop="appSecret">
        <ElInput v-model="formData.appSecret" placeholder="请输入应用密钥" />
      </ElFormItem>
      <ElFormItem label="应用类型" prop="appType">
        <ElInput v-model="formData.appType" placeholder="请输入应用类型" />
      </ElFormItem>
      <ElFormItem label="回调地址" prop="redirectUri">
        <ElInput v-model="formData.redirectUri" placeholder="请输入回调地址" />
      </ElFormItem>

      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
