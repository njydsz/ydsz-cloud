<!--
 * delegate-form 表单页面组件
 *
 * @path apps\workflow-web\src\views\delegate\delegate-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程委托（表单组件）
 * <p>委托规则的创建/编辑表单，临时委托审批权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DelegateApi } from '#/api/delegate';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createDelegateApi, updateDelegateApi } from '#/api/delegate';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  assignee: '',
  delegateTo: '',
  startDate: '',
  endDate: '',
  reason: '',
  status: 0,
});
const rules = {
  assignee: [{ required: true, message: '请输入委派人', trigger: 'blur' }],
  delegateTo: [{ required: true, message: '请输入被委派人', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DelegateApi.DelegateVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        assignee: data.record.assignee || '',
        delegateTo: data.record.delegateTo || '',
        startDate: data.record.startDate || '',
        endDate: data.record.endDate || '',
        reason: data.record.reason || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  assignee: '',
  delegateTo: '',
  startDate: '',
  endDate: '',
  reason: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateDelegateApi(formData as any); ElMessage.success('更新成功'); }
      else { await createDelegateApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑委派管理' : '新增委派管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="委派人" prop="assignee">
        <ElInput v-model="formData.assignee" placeholder="请输入委派人" />
      </ElFormItem>
      <ElFormItem label="被委派人" prop="delegateTo">
        <ElInput v-model="formData.delegateTo" placeholder="请输入被委派人" />
      </ElFormItem>
      <ElFormItem label="开始日期" prop="startDate">
        <ElInput v-model="formData.startDate" placeholder="请输入开始日期" />
      </ElFormItem>
      <ElFormItem label="结束日期" prop="endDate">
        <ElInput v-model="formData.endDate" placeholder="请输入结束日期" />
      </ElFormItem>
      <ElFormItem label="原因">
        <ElInput v-model="formData.reason" type="textarea" :rows="2" placeholder="请输入原因" />
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
