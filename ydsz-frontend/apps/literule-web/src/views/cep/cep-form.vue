<!--
 * cep-form 表单页面组件
 *
 * @path apps\literule-web\src\views\cep\cep-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 规则 CEP（表单组件）
 * <p>复杂事件处理规则的编辑表单，支持事件模式、时间窗配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CepApi } from '#/api/cep';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createCepApi, updateCepApi } from '#/api/cep';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  cepName: '',
  cepPattern: '',
  windowSize: 0,
  description: '',
  status: 0,
});
const rules = {
  cepName: [{ required: true, message: '请输入CEP名称', trigger: 'blur' }],
  cepPattern: [{ required: true, message: '请输入匹配模式', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: CepApi.CepVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        cepName: data.record.cepName || '',
        cepPattern: data.record.cepPattern || '',
        windowSize: data.record.windowSize || 0,
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  cepName: '',
  cepPattern: '',
  windowSize: 0,
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateCepApi(formData as any); ElMessage.success('更新成功'); }
      else { await createCepApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑CEP复杂事件' : '新增CEP复杂事件'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="CEP名称" prop="cepName">
        <ElInput v-model="formData.cepName" placeholder="请输入CEP名称" />
      </ElFormItem>
      <ElFormItem label="匹配模式">
        <ElInput v-model="formData.cepPattern" type="textarea" :rows="2" placeholder="请输入匹配模式" />
      </ElFormItem>
      <ElFormItem label="窗口大小">
        <ElInputNumber v-model="formData.windowSize" :min="0" :max="999" />
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
