<script lang="ts" setup>
/**
 * 国际化（表单组件）
 * <p>语言条目的编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { LanguageApi } from '#/api/language';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createLanguageApi, updateLanguageApi } from '#/api/language';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  languageCode: '',
  languageName: '',
  nativeName: '',
  sort: 0,
  status: 1,
});

const rules = {
  languageCode: [{ required: true, message: '请输入语言编码', trigger: 'blur' }],
  languageName: [{ required: true, message: '请输入语言名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: LanguageApi.LanguageVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        languageCode: data.record.languageCode,
        languageName: data.record.languageName,
        nativeName: data.record.nativeName || '',
        sort: data.record.sort || 0,
        status: data.record.status,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '', languageCode: '', languageName: '', nativeName: '', sort: 0, status: 1,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateLanguageApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createLanguageApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑语言' : '新增语言'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="语言编码" prop="languageCode">
        <ElInput v-model="formData.languageCode" placeholder="如: zh-CN" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="语言名称" prop="languageName">
        <ElInput v-model="formData.languageName" placeholder="如: 简体中文" />
      </ElFormItem>
      <ElFormItem label="本地名称">
        <ElInput v-model="formData.nativeName" placeholder="如: 简体中文" />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="formData.sort" :min="0" :max="999" />
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
