<script lang="ts" setup>
/**
 * 部门（表单组件）
 * <p>部门的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DeptApi } from '#/api/dept';
import type { CompanyApi } from '#/api/company';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio, ElTreeSelect, ElSelect, ElOption } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createDeptApi, updateDeptApi } from '#/api/dept';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);
const treeData = ref<any[]>([]);
const companyList = ref<CompanyApi.CompanyVO[]>([]);

const formData = reactive({
  id: '',
  deptName: '',
  parentId: '',
  sort: 0,
  leader: '',
  phone: '',
  email: '',
  status: 1,
  companyId: '',
});

const rules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择上级部门', trigger: 'change' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{
      record?: DeptApi.DepartmentVO;
      treeData: any[];
      companyList: CompanyApi.CompanyVO[];
      parentId?: string;
    }>();
    treeData.value = data.treeData || [];
    companyList.value = data.companyList || [];

    if (data.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        deptName: data.record.deptName,
        parentId: data.record.parentId,
        sort: data.record.sort || 0,
        leader: data.record.leader || '',
        phone: data.record.phone || '',
        email: data.record.email || '',
        status: data.record.status,
        companyId: data.record.companyId || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        deptName: '',
        parentId: data.parentId || '',
        sort: 0,
        leader: '',
        phone: '',
        email: '',
        status: 1,
        companyId: '',
      });
    }
  },
  onConfirm: async () => {
    try {
      await formRef.value?.validate();
    } catch {
      return;
    }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateDeptApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createDeptApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑部门' : '新增部门'));
</script>

<template>
  <Modal :title="title">
    <ElForm
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      label-position="right"
    >
      <ElFormItem label="上级部门" prop="parentId">
        <ElTreeSelect
          v-model="formData.parentId"
          :data="[{ id: '0', label: '顶级部门', children: treeData }]"
          :props="{ label: 'label', children: 'children' }"
          node-key="id"
          check-strictly
          placeholder="请选择上级部门"
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="部门名称" prop="deptName">
        <ElInput v-model="formData.deptName" placeholder="请输入部门名称" />
      </ElFormItem>
      <ElFormItem label="负责人">
        <ElInput v-model="formData.leader" placeholder="请输入负责人" />
      </ElFormItem>
      <ElFormItem label="联系电话">
        <ElInput v-model="formData.phone" placeholder="请输入联系电话" />
      </ElFormItem>
      <ElFormItem label="邮箱">
        <ElInput v-model="formData.email" placeholder="请输入邮箱" />
      </ElFormItem>
      <ElFormItem label="公司">
        <ElSelect
          v-model="formData.companyId"
          placeholder="请选择公司"
          clearable
          class="w-full"
        >
          <ElOption
            v-for="item in companyList"
            :key="item.id"
            :label="item.companyName"
            :value="item.id"
          />
        </ElSelect>
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
    </ElForm>
  </Modal>
</template>
