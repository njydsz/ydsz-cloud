<!--
 * rag-form 表单页面组件
 *
 * @path apps\agent-web\src\views\rag\rag-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * Agent RAG 知识库（表单组件）
 * <p>RAG 知识库的创建/编辑表单，支持文档上传、Embedding 模型选择。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RagApi } from '#/api/rag';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createRagApi, updateRagApi } from '#/api/rag';
/** 表单提交成功后触发，通知父级列表页刷新数据 */
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  knowledgeName: '',
  sourceType: '',
  sourcePath: '',
  chunkSize: 0,
  chunkOverlap: 0,
  status: 0,
});
const rules = {
  knowledgeName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RagApi.RagVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        knowledgeName: data.record.knowledgeName || '',
        sourceType: data.record.sourceType || '',
        sourcePath: data.record.sourcePath || '',
        chunkSize: data.record.chunkSize || 0,
        chunkOverlap: data.record.chunkOverlap || 0,
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  knowledgeName: '',
  sourceType: '',
  sourcePath: '',
  chunkSize: 0,
  chunkOverlap: 0,
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateRagApi(formData as any); ElMessage.success('更新成功'); }
      else { await createRagApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑RAG知识库' : '新增RAG知识库'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="知识库名称" prop="knowledgeName">
        <ElInput v-model="formData.knowledgeName" placeholder="请输入知识库名称" />
      </ElFormItem>
      <ElFormItem label="数据源类型" prop="sourceType">
        <ElInput v-model="formData.sourceType" placeholder="请输入数据源类型" />
      </ElFormItem>
      <ElFormItem label="数据源路径" prop="sourcePath">
        <ElInput v-model="formData.sourcePath" placeholder="请输入数据源路径" />
      </ElFormItem>
      <ElFormItem label="分块大小">
        <ElInputNumber v-model="formData.chunkSize" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="分块重叠">
        <ElInputNumber v-model="formData.chunkOverlap" :min="0" :max="999" />
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
