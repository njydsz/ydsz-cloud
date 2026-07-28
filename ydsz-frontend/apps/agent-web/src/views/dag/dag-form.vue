<script lang="ts" setup>
/**
 * Agent DAG 编排（表单组件）
 * <p>DAG 编排的可视化编辑器表单，支持拖拽节点、配置连线。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DagApi } from '#/api/dag';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createDagApi, updateDagApi } from '#/api/dag';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  dagName: '',
  dagConfig: '',
  description: '',
  status: 0,
});
const rules = {
  dagName: [{ required: true, message: '请输入DAG名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DagApi.DagVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        dagName: data.record.dagName || '',
        dagConfig: data.record.dagConfig || '',
        description: data.record.description || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  dagName: '',
  dagConfig: '',
  description: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateDagApi(formData as any); ElMessage.success('更新成功'); }
      else { await createDagApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑DAG编排' : '新增DAG编排'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="DAG名称" prop="dagName">
        <ElInput v-model="formData.dagName" placeholder="请输入DAG名称" />
      </ElFormItem>
      <ElFormItem label="DAG配置JSON">
        <ElInput v-model="formData.dagConfig" type="textarea" :rows="2" placeholder="请输入DAG配置JSON" />
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
