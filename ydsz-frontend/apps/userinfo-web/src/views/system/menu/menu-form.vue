<!--
 * menu-form 表单页面组件
 *
 * @path apps\userinfo-web\src\views\system\menu\menu-form.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 菜单（表单组件）
 * <p>菜单的创建/编辑表单。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { MenuApi } from '#/api/menu';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio, ElTreeSelect, ElSelect, ElOption } from 'element-plus';
import { computed, reactive, ref } from 'vue';

import { createMenuApi, updateMenuApi } from '#/api/menu';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);
const treeData = ref<any[]>([]);

const formData = reactive({
  id: '',
  menuName: '',
  parentId: '0',
  menuType: 1,
  path: '',
  component: '',
  icon: '',
  permission: '',
  sort: 0,
  visible: 1,
  status: 1,
});

const rules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{
      record?: MenuApi.MenuVO;
      tableData: any[];
      parentId?: string;
    }>();
    treeData.value = data.tableData || [];

    if (data.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        menuName: data.record.menuName,
        parentId: data.record.parentId,
        menuType: data.record.menuType,
        path: data.record.path || '',
        component: data.record.component || '',
        icon: data.record.icon || '',
        permission: data.record.permission || '',
        sort: data.record.sort || 0,
        visible: data.record.visible ?? 1,
        status: data.record.status,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '', menuName: '', parentId: data.parentId || '0', menuType: 1,
        path: '', component: '', icon: '', permission: '', sort: 0, visible: 1, status: 1,
      });
    }
  },
  onConfirm: async () => {
    try { await formRef.value?.validate(); } catch { return; }
    modalApi.lock();
    try {
      if (isEdit.value) {
        await updateMenuApi(formData as any);
        ElMessage.success('更新成功');
      } else {
        await createMenuApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑菜单' : '新增菜单'));

const menuTypeOptions = [
  { label: '目录', value: 0 },
  { label: '菜单', value: 1 },
  { label: '按钮', value: 2 },
];
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
      <ElFormItem label="上级菜单">
        <ElTreeSelect
          v-model="formData.parentId"
          :data="[{ id: '0', label: '顶级菜单', children: treeData }]"
          :props="{ label: 'label', children: 'children' }"
          node-key="id"
          check-strictly
          placeholder="请选择上级菜单"
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="菜单类型">
        <ElRadioGroup v-model="formData.menuType">
          <ElRadio v-for="opt in menuTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="菜单名称" prop="menuName">
        <ElInput v-model="formData.menuName" placeholder="请输入菜单名称" />
      </ElFormItem>
      <ElFormItem v-if="formData.menuType !== 2" label="路由路径">
        <ElInput v-model="formData.path" placeholder="请输入路由路径" />
      </ElFormItem>
      <ElFormItem v-if="formData.menuType === 1" label="组件路径">
        <ElInput v-model="formData.component" placeholder="请输入组件路径" />
      </ElFormItem>
      <ElFormItem v-if="formData.menuType !== 0" label="权限标识">
        <ElInput v-model="formData.permission" placeholder="如: system:user:add" />
      </ElFormItem>
      <ElFormItem v-if="formData.menuType !== 2" label="图标">
        <ElInput v-model="formData.icon" placeholder="请输入图标名称" />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="formData.sort" :min="0" :max="999" />
      </ElFormItem>
      <ElFormItem v-if="formData.menuType !== 2" label="是否显示">
        <ElRadioGroup v-model="formData.visible">
          <ElRadio :value="1">显示</ElRadio>
          <ElRadio :value="0">隐藏</ElRadio>
        </ElRadioGroup>
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
