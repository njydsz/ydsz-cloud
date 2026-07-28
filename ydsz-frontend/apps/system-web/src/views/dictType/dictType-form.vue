<script lang="ts" setup>
/**
 * 字典类型（表单组件）
 * <p>字典类型的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DicttypeApi } from '#/api/dictType';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createDicttypeApi, updateDicttypeApi } from '#/api/dictType';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  typeCode: '',
  typeName: '',
  remark: '',
  status: 1,
});

const rules = {
  typeCode: [{ required: true, message: '请输入类型编码', trigger: 'blur' }],
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DicttypeApi.DicttypeVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        typeCode: data.record.typeCode || '',
        typeName: data.record.typeName || '',
        remark: data.record.remark || '',
        status: data.record.status || 1,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        typeCode: '',
        typeName: '',
        remark: '',
        status: 1,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateDicttypeApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createDicttypeApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑字典' : '新增字典'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="类型编码" prop="typeCode">
        <ElInput v-model="formData.typeCode" placeholder="请输入类型编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="类型名称" prop="typeName">
        <ElInput v-model="formData.typeName" placeholder="请输入类型名称" />
      </ElFormItem>

      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
