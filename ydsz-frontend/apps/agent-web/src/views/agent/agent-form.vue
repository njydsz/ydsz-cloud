<!--
 * agent-form 表单页面组件
 *
 * @path apps\agent-web\src\views\agent\agent-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * Agent 管理（表单组件）
 * <p>Agent 智能体的创建/编辑表单，作为 {@code useVbenModal} 的子组件使用。
 * <p>支持 Agent 名称、类型、模型提供商/名称、系统提示词、温度等字段。
 * <p>通过 {@code emit('success')} 通知父组件刷新列表。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { AgentApi } from '#/api/agent';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createAgentApi, updateAgentApi } from '#/api/agent';
/** 表单提交成功后触发，通知父级列表页刷新数据 */
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  agentName: '',
  agentType: '',
  modelProvider: '',
  modelName: '',
  systemPrompt: '',
  temperature: 0,
  status: 0,
});
const rules = {
  agentName: [{ required: true, message: '请输入Agent名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: AgentApi.AgentVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        agentName: data.record.agentName || '',
        agentType: data.record.agentType || '',
        modelProvider: data.record.modelProvider || '',
        modelName: data.record.modelName || '',
        systemPrompt: data.record.systemPrompt || '',
        temperature: data.record.temperature || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  agentName: '',
  agentType: '',
  modelProvider: '',
  modelName: '',
  systemPrompt: '',
  temperature: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateAgentApi(formData as any); ElMessage.success('更新成功'); }
      else { await createAgentApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑Agent管理' : '新增Agent管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="Agent名称" prop="agentName">
        <ElInput v-model="formData.agentName" placeholder="请输入Agent名称" />
      </ElFormItem>
      <ElFormItem label="Agent类型" prop="agentType">
        <ElInput v-model="formData.agentType" placeholder="请输入Agent类型" />
      </ElFormItem>
      <ElFormItem label="模型提供商" prop="modelProvider">
        <ElInput v-model="formData.modelProvider" placeholder="请输入模型提供商" />
      </ElFormItem>
      <ElFormItem label="模型名称" prop="modelName">
        <ElInput v-model="formData.modelName" placeholder="请输入模型名称" />
      </ElFormItem>
      <ElFormItem label="系统提示词">
        <ElInput v-model="formData.systemPrompt" type="textarea" :rows="2" placeholder="请输入系统提示词" />
      </ElFormItem>
      <ElFormItem label="温度参数">
        <ElInputNumber v-model="formData.temperature" :min="0" :max="999" />
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
