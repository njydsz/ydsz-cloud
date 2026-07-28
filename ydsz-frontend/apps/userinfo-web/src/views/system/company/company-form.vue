<script lang="ts" setup>
/**
 * 公司（表单组件）
 * <p>公司的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CompanyApi } from '#/api/company';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElMessage, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createCompanyApi, updateCompanyApi } from '#/api/company';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  companyName: '',
  legalPerson: '',
  contactPhone: '',
  contactEmail: '',
  address: '',
  status: 1,
  remark: '',
});

const rules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: CompanyApi.CompanyVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        companyName: data.record.companyName,
        legalPerson: data.record.legalPerson || '',
        contactPhone: data.record.contactPhone || '',
        contactEmail: data.record.contactEmail || '',
        address: data.record.address || '',
        status: data.record.status,
        remark: data.record.remark || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '', companyName: '', legalPerson: '', contactPhone: '', contactEmail: '', address: '', status: 1, remark: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateCompanyApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createCompanyApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑公司' : '新增公司'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="公司名称" prop="companyName">
        <ElInput v-model="formData.companyName" placeholder="请输入公司名称" />
      </ElFormItem>
      <ElFormItem label="法人">
        <ElInput v-model="formData.legalPerson" placeholder="请输入法人" />
      </ElFormItem>
      <ElFormItem label="联系电话">
        <ElInput v-model="formData.contactPhone" placeholder="请输入联系电话" />
      </ElFormItem>
      <ElFormItem label="联系邮箱">
        <ElInput v-model="formData.contactEmail" placeholder="请输入联系邮箱" />
      </ElFormItem>
      <ElFormItem label="地址">
        <ElInput v-model="formData.address" type="textarea" :rows="2" placeholder="请输入地址" />
      </ElFormItem>
      <ElFormItem label="状态">
        <ElRadioGroup v-model="formData.status">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
