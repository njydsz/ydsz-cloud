<!--
 * dsl-form 表单页面组件
 *
 * @path apps\literule-web\src\views\dsl\dsl-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 规则 DSL（编辑器组件）
 * <p>规则 DSL 的编辑器组件，支持 JSON/YAML 格式的脚本编写、校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DslApi } from '#/api/dsl';
import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';
import { createDslApi, updateDslApi } from '#/api/dsl';
const emit = defineEmits<{ success: [] }>();
const formRef = ref();
const isEdit = ref(false);
const formData = reactive({ id: '',
  dslName: '',
  dslType: '',
  dslContent: '',
  status: 0,
});
const rules = {
  dslName: [{ required: true, message: '请输入DSL名称', trigger: 'blur' }],
};
const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DslApi.DslVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, { id: data.record.id,
        dslName: data.record.dslName || '',
        dslType: data.record.dslType || '',
        dslContent: data.record.dslContent || '',
        status: data.record.status || 0,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '',
  dslName: '',
  dslType: '',
  dslContent: '',
  status: 0,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) { await updateDslApi(formData as any); ElMessage.success('更新成功'); }
      else { await createDslApi(formData as any); ElMessage.success('创建成功'); }
      emit('success'); modalApi.close();
    } finally { modalApi.unlock(); }
  },
});
const title = computed(() => (isEdit.value ? '编辑DSL管理' : '新增DSL管理'));
</script>
<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="DSL名称" prop="dslName">
        <ElInput v-model="formData.dslName" placeholder="请输入DSL名称" />
      </ElFormItem>
      <ElFormItem label="DSL类型" prop="dslType">
        <ElInput v-model="formData.dslType" placeholder="请输入DSL类型" />
      </ElFormItem>
      <ElFormItem label="DSL内容">
        <ElInput v-model="formData.dslContent" type="textarea" :rows="2" placeholder="请输入DSL内容" />
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
