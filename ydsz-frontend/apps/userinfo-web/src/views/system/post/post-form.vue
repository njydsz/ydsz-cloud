<!--
 * post-form 表单页面组件
 *
 * @path apps\userinfo-web\src\views\system\post\post-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 岗位（表单组件）
 * <p>岗位的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PostApi } from '#/api/post';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createPostApi, updatePostApi } from '#/api/post';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  postCode: '',
  postName: '',
  sort: 0,
  status: 1,
  remark: '',
});

const rules = {
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: PostApi.PostVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        postCode: data.record.postCode,
        postName: data.record.postName,
        sort: data.record.sort || 0,
        status: data.record.status,
        remark: data.record.remark || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, { id: '', postCode: '', postName: '', sort: 0, status: 1, remark: '' });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updatePostApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createPostApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑岗位' : '新增岗位'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="岗位名称" prop="postName">
        <ElInput v-model="formData.postName" placeholder="请输入岗位名称" />
      </ElFormItem>
      <ElFormItem label="岗位编码" prop="postCode">
        <ElInput v-model="formData.postCode" placeholder="请输入岗位编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="formData.sort" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem label="状态">
        <ElRadioGroup v-model="formData.status">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
