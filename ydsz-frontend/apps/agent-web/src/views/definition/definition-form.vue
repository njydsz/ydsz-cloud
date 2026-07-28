<script lang="ts" setup>
/**
 * Agent 工具定义（表单组件）
 * <p>工具的元数据编辑表单，包含工具名称/描述/参数 Schema/调用方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DefinitionApi } from '#/api/definition';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createDefinitionApi, updateDefinitionApi } from '#/api/definition';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  defName: '',
  defCode: '',
  agentType: '',
  config: '',
  description: '',
  status: 0,
});
const rules = {
  defName: [{ required: true, message: '请输入定义名称', trigger: 'blur' }],
  defCode: [{ required: true, message: '请输入定义编码', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DefinitionApi.DefinitionVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        defName: data.record.defName || '',
        defCode: data.record.defCode || '',
        agentType: data.record.agentType || '',
        config: data.record.config || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  defName: '',
  defCode: '',
  agentType: '',
  config: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateDefinitionApi(formData as any); ElMessage.success('更新成功'); }
      else { await createDefinitionApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑Agent定义' : '新增Agent定义'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="定义名称" prop="defName">
        <ElInput v-model="formData.defName" placeholder="请输入定义名称" />
      </ElFormItem>
      <ElFormItem label="定义编码" prop="defCode">
        <ElInput v-model="formData.defCode" placeholder="请输入定义编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="Agent类型" prop="agentType">
        <ElInput v-model="formData.agentType" placeholder="请输入Agent类型" />
      </ElFormItem>
      <ElFormItem label="配置JSON">
        <ElInput v-model="formData.config" type="textarea" :rows="2" placeholder="请输入配置JSON" />
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
