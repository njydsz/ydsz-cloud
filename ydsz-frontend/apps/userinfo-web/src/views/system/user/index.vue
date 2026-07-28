<script lang="ts" setup>
/**
 * 用户（列表页）
 * <p>用户（{@code ydsz_user}）的列表/分页查询页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';
import { getVxePopupContainer } from '@ydsz/utils';

import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElMessageBox, ElSelect, ElOption, ElTag } from 'element-plus';
import { computed, h, onMounted, reactive, ref } from 'vue';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  assignUserRolesApi,
  deleteUserApi,
  getUserPageApi,
  getUserRolesApi,
  resetPasswordApi,
  type UserApi,
} from '#/api/user';
import { getDeptTreeApi } from '#/api/dept';
import { getRoleListApi, type RoleApi } from '#/api/role';
import { getCompanyListApi, type CompanyApi } from '#/api/company';
import { getPostListApi, type PostApi } from '#/api/post';

import UserForm from './user-form.vue';
import RoleAssign from './role-assign.vue';

defineOptions({ name: 'UserManagement' });

const deptTreeData = ref<any[]>([]);
const roleList = ref<RoleApi.RoleVO[]>([]);
const companyList = ref<CompanyApi.CompanyVO[]>([]);
const postList = ref<PostApi.PostVO[]>([]);

const searchForm = reactive({
  username: '',
  realName: '',
  phone: '',
  status: undefined as number | undefined,
  deptId: undefined as string | undefined,
});

async function loadDeptTree() {
  try {
    deptTreeData.value = await getDeptTreeApi();
  } catch {
    // ignore
  }
}

async function loadRoleList() {
  try {
    roleList.value = await getRoleListApi();
  } catch {
    // ignore
  }
}

async function loadCompanyList() {
  try {
    companyList.value = await getCompanyListApi();
  } catch {
    // ignore
  }
}

async function loadPostList() {
  try {
    postList.value = await getPostListApi();
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadDeptTree();
  loadRoleList();
  loadCompanyList();
  loadPostList();
});

const gridOptions: VxeGridProps<UserApi.UserAccountVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'username', title: '用户名', width: 120 },
    { field: 'realName', title: '真实姓名', width: 100 },
    { field: 'phone', title: '手机号', width: 130 },
    { field: 'email', title: '邮箱', width: 180 },
    { field: 'deptName', title: '部门', width: 120 },
    { field: 'postName', title: '岗位', width: 100 },
    { field: 'companyName', title: '公司', width: 120 },
    {
      field: 'status',
      title: '状态',
      width: 80,
      slots: {
        default: ({ row }) => {
          const isEnable = row.status === 1;
          return h(
            ElTag,
            { type: isEnable ? 'success' : 'danger', size: 'small' },
            () => (isEnable ? '启用' : '禁用'),
          );
        },
      },
    },
    { field: 'lastLoginTime', title: '最后登录', width: 160 },
    { field: 'createTime', title: '创建时间', width: 160 },
    {
      field: 'action',
      title: '操作',
      width: 260,
      fixed: 'right',
      slots: {
        default: ({ row }) => {
          return h('div', { class: 'flex gap-1' }, [
            h(
              ElButton,
              {
                size: 'small',
                link: true,
                type: 'primary',
                onClick: () => handleEdit(row),
              },
              () => '编辑',
            ),
            h(
              ElButton,
              {
                size: 'small',
                link: true,
                type: 'warning',
                onClick: () => handleAssignRoles(row),
              },
              () => '分配角色',
            ),
            h(
              ElButton,
              {
                size: 'small',
                link: true,
                type: 'info',
                onClick: () => handleResetPassword(row),
              },
              () => '重置密码',
            ),
            h(
              ElButton,
              {
                size: 'small',
                link: true,
                type: 'danger',
                onClick: () => handleDelete(row),
              },
              () => '删除',
            ),
          ]);
        },
      },
    },
  ],
  height: 'auto',
  pagerConfig: {
    pageSize: 20,
    pageSizes: [10, 20, 50, 100],
  },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getUserPageApi({
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          ...searchForm,
          ...formValues,
        });
      },
    },
  },
  toolbarConfig: {
    custom: true,
    refresh: { code: 'query' },
    search: true,
    zoom: true,
  },
  formConfig: {
    enabled: true,
    items: [
      {
        field: 'username',
        title: '用户名',
        itemRender: { name: 'Input', props: { placeholder: '请输入用户名' } },
      },
      {
        field: 'realName',
        title: '真实姓名',
        itemRender: { name: 'Input', props: { placeholder: '请输入真实姓名' } },
      },
      {
        field: 'phone',
        title: '手机号',
        itemRender: { name: 'Input', props: { placeholder: '请输入手机号' } },
      },
      {
        field: 'status',
        title: '状态',
        itemRender: {
          name: 'Select',
          props: {
            placeholder: '请选择状态',
            options: [
              { label: '启用', value: 1 },
              { label: '禁用', value: 0 },
            ],
          },
        },
      },
    ],
  },
};

const [Grid, gridApi] = useYDSZVxeGrid({
  gridOptions,
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: '用户名' },
        fieldName: 'username',
        label: '用户名',
      },
      {
        component: 'Input',
        componentProps: { placeholder: '真实姓名' },
        fieldName: 'realName',
        label: '真实姓名',
      },
      {
        component: 'Input',
        componentProps: { placeholder: '手机号' },
        fieldName: 'phone',
        label: '手机号',
      },
      {
        component: 'Select',
        componentProps: {
          placeholder: '状态',
          options: [
            { label: '启用', value: 1 },
            { label: '禁用', value: 0 },
          ],
        },
        fieldName: 'status',
        label: '状态',
      },
    ],
    submitOnChange: false,
    collapsed: false,
    collapsedTriggerResize: true,
    showCollapseButton: true,
    submitButtonOptions: { content: '搜索' },
    resetButtonOptions: { content: '重置' },
  },
});

function handleSearch() {
  gridApi.query();
}

function handleReset() {
  Object.assign(searchForm, {
    username: '',
    realName: '',
    phone: '',
    status: undefined,
    deptId: undefined,
  });
  gridApi.query();
}

// ========== 表单弹窗 ==========
const [UserFormModal, userFormApi] = useVbenModal({
  connectedComponent: UserForm,
});

function handleAdd() {
  userFormApi.setData({
    deptTreeData: deptTreeData.value,
    companyList: companyList.value,
    postList: postList.value,
  });
  userFormApi.open();
}

function handleEdit(row: UserApi.UserAccountVO) {
  userFormApi.setData({
    record: row,
    deptTreeData: deptTreeData.value,
    companyList: companyList.value,
    postList: postList.value,
  });
  userFormApi.open();
}

// ========== 角色分配弹窗 ==========
const [RoleAssignModal, roleAssignApi] = useVbenModal({
  connectedComponent: RoleAssign,
});

async function handleAssignRoles(row: UserApi.UserAccountVO) {
  const roleIds = await getUserRolesApi(row.id);
  roleAssignApi.setData({
    userId: row.id,
    username: row.username,
    roleList: roleList.value,
    selectedRoleIds: roleIds,
  });
  roleAssignApi.open();
}

// ========== 重置密码 ==========
async function handleResetPassword(row: UserApi.UserAccountVO) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入用户「${row.username}」的新密码`,
      '重置密码',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /.{6,}/,
        inputErrorMessage: '密码至少6位',
      },
    );
    await resetPasswordApi({ userId: row.id, newPassword: value });
    ElMessage.success('密码重置成功');
  } catch {
    // user cancelled
  }
}

// ========== 删除 ==========
async function handleDelete(row: UserApi.UserAccountVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？`,
      '删除确认',
      { type: 'warning' },
    );
    await deleteUserApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // user cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="用户管理">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增用户</ElButton>
        <ElButton @click="handleReset">重置搜索</ElButton>
      </template>
    </Grid>
    <UserFormModal @success="gridApi.query()" />
    <RoleAssignModal @success="gridApi.query()" />
  </Page>
</template>
