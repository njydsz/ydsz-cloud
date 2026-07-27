<script lang="ts" setup>
import type { RouteRuleApi } from '#/api/routeRule';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createRouteRuleApi, updateRouteRuleApi } from '#/api/routeRule';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  ruleName: '',
  channel: '',
  priority: 0,
  condition: '',
  targetChannel: '',
  status: 0,
});
const rules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RouteRuleApi.RouteRuleVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        ruleName: data.record.ruleName || '',
        channel: data.record.channel || '',
        priority: data.record.priority || 0,
        condition: data.record.condition || '',
        targetChannel: data.record.targetChannel || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  ruleName: '',
  channel: '',
  priority: 0,
  condition: '',
  targetChannel: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateRouteRuleApi(formData as any); ElMessage.success('更新成功'); }
      else { await createRouteRuleApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑路由规则' : '新增路由规则'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="规则名称" prop="ruleName">
        <ElInput v-model="formData.ruleName" placeholder="请输入规则名称" />
      </ElFormItem>
      <ElFormItem label="通道" prop="channel">
        <ElInput v-model="formData.channel" placeholder="请输入通道" />
      </ElFormItem>
      <ElFormItem label="优先级">
        <ElInputNumber v-model="formData.priority" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="条件">
        <ElInput v-model="formData.condition" type="textarea" :rows="2" placeholder="请输入条件" />
      </ElFormItem>
      <ElFormItem label="目标通道" prop="targetChannel">
        <ElInput v-model="formData.targetChannel" placeholder="请输入目标通道" />
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
