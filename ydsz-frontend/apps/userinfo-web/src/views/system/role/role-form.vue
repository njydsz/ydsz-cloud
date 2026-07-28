<!--
 * 角色表单组件 — 支持新增/编辑角色信息及权限分配
 *
 * @path apps\userinfo-web\src\views\system\role\role-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 角色（表单组件）
 * <p>角色的创建/编辑表单，包含权限分配。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RoleApi } from '#/api/role';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio, ElSelect, ElOption } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createRoleApi, updateRoleApi } from '#/api/role';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({
  id: '',
  roleCode: '',
  roleName: '',
  dataScope: 1,
  sort: 0,
  status: 1,
  remark: '',
});

const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{ record?: RoleApi.RoleVO }>();
    if (data?.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        roleCode: data.record.roleCode,
        roleName: data.record.roleName,
        dataScope: data.record.dataScope || 1,
        sort: data.record.sort || 0,
        status: data.record.status,
        remark: data.record.remark || '',
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '', roleCode: '', roleName: '', dataScope: 1, sort: 0, status: 1, remark: '',
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateRoleApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createRoleApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑角色' : '新增角色'));

const dataScopeOptions = [
  { label: '全部数据', value: 1 },
  { label: '自定义数据', value: 2 },
  { label: '本部门', value: 3 },
  { label: '本部门及以下', value: 4 },
  { label: '仅本人', value: 5 },
];
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="角色名称" prop="roleName">
        <ElInput v-model="formData.roleName" placeholder="请输入角色名称" />
      </ElFormItem>
      <ElFormItem label="角色编码" prop="roleCode">
        <ElInput v-model="formData.roleCode" placeholder="请输入角色编码" :disabled="isEdit" />
      </ElFormItem>
      <ElFormItem label="数据范围">
        <ElSelect v-model="formData.dataScope" placeholder="请选择数据范围" class="w-full">
          <ElOption v-for="opt in dataScopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
      <ElFormItem label="备注">
        <ElInput v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
