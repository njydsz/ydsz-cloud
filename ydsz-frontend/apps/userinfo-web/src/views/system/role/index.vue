<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag, ElTransfer } from 'element-plus';
import { h, onMounted, ref } from 'vue';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  deleteRoleApi,
  getRolePageApi,
  assignRolePermissionsApi,
  getRolePermissionsApi,
  type RoleApi,
} from '#/api/role';
import { getMenuListApi, type MenuApi } from '#/api/menu';

import RoleForm from './role-form.vue';

defineOptions({ name: 'RoleManagement' });

const menuList = ref<MenuApi.MenuVO[]>([]);

async function loadMenuList() {
  try {
    menuList.value = await getMenuListApi();
  } catch {
    // ignore
  }
}

onMounted(loadMenuList);

const gridOptions: VxeGridProps<RoleApi.RoleVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'roleName', title: '角色名称', width: 150 },
    { field: 'roleCode', title: '角色编码', width: 150 },
    { field: 'dataScope', title: '数据范围', width: 120, slots: {
      default: ({ row }) => {
        const scopes: Record<number, string> = {
          1: '全部数据', 2: '自定义数据', 3: '本部门', 4: '本部门及以下', 5: '仅本人',
        };
        return h('span', null, scopes[row.dataScope] || '-');
      },
    }},
    { field: 'sort', title: '排序', width: 80, align: 'center' },
    {
      field: 'status',
      title: '状态',
      width: 80,
      slots: {
        default: ({ row }) => {
          const isEnable = row.status === 1;
          return h(ElTag, { type: isEnable ? 'success' : 'danger', size: 'small' },
            () => (isEnable ? '启用' : '禁用'));
        },
      },
    },
    { field: 'remark', title: '备注', minWidth: 150 },
    { field: 'createTime', title: '创建时间', width: 160 },
    {
      field: 'action',
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: {
        default: ({ row }) => {
          return h('div', { class: 'flex gap-1' }, [
            h(ElButton, { size: 'small', link: true, type: 'primary', onClick: () => handleEdit(row) }, () => '编辑'),
            h(ElButton, { size: 'small', link: true, type: 'warning', onClick: () => handleAssignPermissions(row) }, () => '分配权限'),
            h(ElButton, { size: 'small', link: true, type: 'danger', onClick: () => handleDelete(row) }, () => '删除'),
          ]);
        },
      },
    },
  ],
  height: 'auto',
  pagerConfig: { pageSize: 20, pageSizes: [10, 20, 50, 100] },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getRolePageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          ...formValues,
        });
      },
    },
  },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: {
    enabled: true,
    items: [
      { field: 'roleName', title: '角色名称', itemRender: { name: 'Input', props: { placeholder: '角色名称' } } },
      { field: 'roleCode', title: '角色编码', itemRender: { name: 'Input', props: { placeholder: '角色编码' } } },
      {
        field: 'status',
        title: '状态',
        itemRender: {
          name: 'Select',
          props: {
            placeholder: '状态',
            options: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }],
          },
        },
      },
    ],
  },
};

const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });

// ========== Form ==========
const [RoleFormModal, roleFormApi] = useVbenModal({ connectedComponent: RoleForm });

function handleAdd() {
  roleFormApi.open();
}

function handleEdit(row: RoleApi.RoleVO) {
  roleFormApi.setData({ record: row });
  roleFormApi.open();
}

// ========== Permission Assign ==========
const permDialogVisible = ref(false);
const currentRoleId = ref('');
const currentRoleName = ref('');
const selectedPermIds = ref<string[]>([]);
const permTransferData = ref<{ label: string; key: string }[]>([]);

async function handleAssignPermissions(row: RoleApi.RoleVO) {
  currentRoleId.value = row.id;
  currentRoleName.value = row.roleName;
  permTransferData.value = menuList.value.map((m) => ({
    label: `${m.menuName}${m.permission ? ` (${m.permission})` : ''}`,
    key: m.id,
  }));
  const existing = await getRolePermissionsApi(row.id);
  selectedPermIds.value = [...existing];
  permDialogVisible.value = true;
}

async function confirmPermissionAssign() {
  await assignRolePermissionsApi(currentRoleId.value, selectedPermIds.value);
  ElMessage.success('权限分配成功');
  permDialogVisible.value = false;
}

// ========== Delete ==========
async function handleDelete(row: RoleApi.RoleVO) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '删除确认', { type: 'warning' });
    await deleteRoleApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="角色管理">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增角色</ElButton>
      </template>
    </Grid>
    <RoleFormModal @success="gridApi.query()" />

    <ElDialog
      v-model="permDialogVisible"
      :title="`分配权限 - ${currentRoleName}`"
      width="600px"
    >
      <ElTransfer
        v-model="selectedPermIds"
        :data="permTransferData"
        :titles="['可选权限', '已分配权限']"
        filterable
        filter-placeholder="搜索权限"
      />
      <template #footer>
        <ElButton @click="permDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="confirmPermissionAssign">确定</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
