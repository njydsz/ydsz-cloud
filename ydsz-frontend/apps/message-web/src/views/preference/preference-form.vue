<!--
 * preference-form 表单页面组件
 *
 * @path apps\message-web\src\views\preference\preference-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 消息偏好（表单组件）
 * <p>用户偏好设置表单，按渠道订阅/退订、免打扰时段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PreferenceApi } from '#/api/preference';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createPreferenceApi, updatePreferenceApi } from '#/api/preference';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  userId: '',
  channel: '',
  dndEnabled: 0,
  dndStart: '',
  dndEnd: '',
  status: 0,
});
const rules = {
  userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: PreferenceApi.PreferenceVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        userId: data.record.userId || '',
        channel: data.record.channel || '',
        dndEnabled: data.record.dndEnabled || 0,
        dndStart: data.record.dndStart || '',
        dndEnd: data.record.dndEnd || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  userId: '',
  channel: '',
  dndEnabled: 0,
  dndStart: '',
  dndEnd: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updatePreferenceApi(formData as any); ElMessage.success('更新成功'); }
      else { await createPreferenceApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑消息偏好' : '新增消息偏好'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="用户ID" prop="userId">
        <ElInput v-model="formData.userId" placeholder="请输入用户ID" />
      </ElFormItem>
      <ElFormItem label="通道" prop="channel">
        <ElInput v-model="formData.channel" placeholder="请输入通道" />
      </ElFormItem>
      <ElFormItem label="免打扰">
        <ElRadioGroup v-model="formData.dndEnabled">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="免打扰开始" prop="dndStart">
        <ElInput v-model="formData.dndStart" placeholder="请输入免打扰开始" />
      </ElFormItem>
      <ElFormItem label="免打扰结束" prop="dndEnd">
        <ElInput v-model="formData.dndEnd" placeholder="请输入免打扰结束" />
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
