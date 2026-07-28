<!--
 * 项目立项（表单组件）
 *
 * @path apps\project-web\src\views\initiation\initiation-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目立项（表单组件）
 * <p>项目立项的创建/审批表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { InitiationApi } from '#/api/initiation';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createInitiationApi, updateInitiationApi } from '#/api/initiation';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  projectCode: '',
  projectName: '',
  projectManager: '',
  projectType: '',
  startDate: '',
  endDate: '',
  totalBudget: 0,
  status: 0,
});
const rules = {
  projectCode: [{ required: true, message: '请输入项目编号', trigger: 'blur' }],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: InitiationApi.InitiationVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        projectCode: data.record.projectCode || '',
        projectName: data.record.projectName || '',
        projectManager: data.record.projectManager || '',
        projectType: data.record.projectType || '',
        startDate: data.record.startDate || '',
        endDate: data.record.endDate || '',
        totalBudget: data.record.totalBudget || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  projectCode: '',
  projectName: '',
  projectManager: '',
  projectType: '',
  startDate: '',
  endDate: '',
  totalBudget: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateInitiationApi(formData as any); ElMessage.success('更新成功'); }
      else { await createInitiationApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑项目立项' : '新增项目立项'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="项目编号" prop="projectCode">
        <ElInput v-model="formData.projectCode" placeholder="请输入项目编号" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="项目名称" prop="projectName">
        <ElInput v-model="formData.projectName" placeholder="请输入项目名称" />
      </ElFormItem>
      <ElFormItem label="项目经理" prop="projectManager">
        <ElInput v-model="formData.projectManager" placeholder="请输入项目经理" />
      </ElFormItem>
      <ElFormItem label="项目类型" prop="projectType">
        <ElInput v-model="formData.projectType" placeholder="请输入项目类型" />
      </ElFormItem>
      <ElFormItem label="开始日期" prop="startDate">
        <ElInput v-model="formData.startDate" placeholder="请输入开始日期" />
      </ElFormItem>
      <ElFormItem label="结束日期" prop="endDate">
        <ElInput v-model="formData.endDate" placeholder="请输入结束日期" />
      </ElFormItem>
      <ElFormItem label="总预算">
        <ElInputNumber v-model="formData.totalBudget" :min="0" :max="999" />
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
