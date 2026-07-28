<!--
 * 任务分组（表单组件）
 *
 * @path apps\cronjob-web\src\views\jobGroup\jobGroup-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 任务分组（表单组件）
 * <p>任务分组的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { JobGroupApi } from '#/api/jobGroup';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createJobGroupApi, updateJobGroupApi } from '#/api/jobGroup';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  groupName: '',
  appname: '',
  addressList: '',
  status: 0,
});
const rules = {
  groupName: [{ required: true, message: '请输入分组名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: JobGroupApi.JobGroupVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        groupName: data.record.groupName || '',
        appname: data.record.appname || '',
        addressList: data.record.addressList || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  groupName: '',
  appname: '',
  addressList: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateJobGroupApi(formData as any); ElMessage.success('更新成功'); }
      else { await createJobGroupApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑任务分组' : '新增任务分组'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="分组名称" prop="groupName">
        <ElInput v-model="formData.groupName" placeholder="请输入分组名称" />
      </ElFormItem>
      <ElFormItem label="AppName" prop="appname">
        <ElInput v-model="formData.appname" placeholder="请输入AppName" />
      </ElFormItem>
      <ElFormItem label="地址列表">
        <ElInput v-model="formData.addressList" type="textarea" :rows="2" placeholder="请输入地址列表" />
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
