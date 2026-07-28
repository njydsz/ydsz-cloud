<!--
 * apps 列表/管理页面组件
 *
 * @path apps\system-web\src\views\app\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 应用（列表页）
 * <p>应用（{@code ydsz_app}）的列表页，管理多应用隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAppApi,
  getAppPageApi,
  type AppApi,
} from '#/api/app';

import AppForm from './app-form.vue';

defineOptions({ name: 'AppManagement' });

const gridOptions: VxeGridProps<AppApi.AppVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'appCode', title: '应用编码', width: 120 },
    { field: 'appName', title: '应用名称', width: 150 },
    { field: 'appType', title: '应用类型', width: 100 },
    { field: 'redirectUri', title: '回调地址', width: 250 },
    { field: 'status', title: '状态', width: 80 },
    { field: 'createTime', title: '创建时间', width: 160 },
    {
      field: 'action',
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: {
        default: ({ row }) => {
          return h('div', { class: 'flex gap-1' }, [
            h(ElButton, { size: 'small', link: true, type: 'primary', onClick: () => handleEdit(row) }, () => '编辑'),
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
        return await getAppPageApi({
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
      { field: 'appName', title: '应用名称', itemRender: { name: 'Input', props: { placeholder: '应用名称' } } },
    ],
  },
};

const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });

const [AppFormModal, appFormApi] = useVbenModal({ connectedComponent: AppForm });

function handleAdd() {
  appFormApi.open();
}

function handleEdit(row: AppApi.AppVO) {
  AppFormApi.setData({ record: row });
  AppFormApi.open();
}

async function handleDelete(row: AppApi.AppVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.appName}」吗？`, '删除确认', { type: 'warning' });
    await deleteAppApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="应用注册">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
    </Grid>
    <AppFormModal @success="gridApi.query()" />
  </Page>
</template>
