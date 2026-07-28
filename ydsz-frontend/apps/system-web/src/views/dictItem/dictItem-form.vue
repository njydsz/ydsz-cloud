<script lang="ts" setup>
/**
 * 字典项（表单组件）
 * <p>字典项的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DictitemApi } from '#/api/dictItem';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createDictitemApi, updateDictitemApi } from '#/api/dictItem';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  typeCode: '',
  itemCode: '',
  itemText: '',
  itemValue: '',
  sort: 0,
  remark: '',
  status: 1,
});

const rules = {
  typeCode: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
  itemCode: [{ required: true, message: '请输入字典项编码', trigger: 'blur' }],
  itemText: [{ required: true, message: '请输入显示文本', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: DictitemApi.DictitemVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        typeCode: data.record.typeCode || '',
        itemCode: data.record.itemCode || '',
        itemText: data.record.itemText || '',
        itemValue: data.record.itemValue || '',
        sort: data.record.sort || 0,
        remark: data.record.remark || '',
        status: data.record.status || 1,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        typeCode: '',
        itemCode: '',
        itemText: '',
        itemValue: '',
        sort: 0,
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
        await updateDictitemApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createDictitemApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑字' : '新增字'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="字典类型" prop="typeCode">
        <ElInput v-model="formData.typeCode" placeholder="请输入字典类型" />
      </ElFormItem>
      <ElFormItem label="字典项编码" prop="itemCode">
        <ElInput v-model="formData.itemCode" placeholder="请输入字典项编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="显示文本" prop="itemText">
        <ElInput v-model="formData.itemText" placeholder="请输入显示文本" />
      </ElFormItem>
      <ElFormItem label="字典值" prop="itemValue">
        <ElInput v-model="formData.itemValue" placeholder="请输入字典值" />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="formData.sort" :min="0" :max="999" />
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
