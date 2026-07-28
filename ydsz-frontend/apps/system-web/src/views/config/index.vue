<script lang="ts" setup>
/**
 * 系统配置（列表页）
 * <p>系统参数（{@code ydsz_config}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  deleteConfigApi,
  getConfigPageApi,
  type ConfigApi,
} from '#/api/config';

import ConfigForm from './config-form.vue';

defineOptions({ name: 'ConfigManagement' });

const gridOptions: VxeGridProps<ConfigApi.ConfigVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'configKey', title: '配置键', width: 160 },
    { field: 'configName', title: '配置名称', width: 150 },
    { field: 'configGroup', title: '配置分组', width: 120 },
    { field: 'valueType', title: '值类型', width: 100 },
    { field: 'configValue', title: '配置值', width: 200 },
    { field: 'remark', title: '备注', width: 150 },
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
        return await getConfigPageApi({
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
      { field: 'configKey', title: '配置键', itemRender: { name: 'Input', props: { placeholder: '配置键' } } },
      { field: 'configGroup', title: '配置分组', itemRender: { name: 'Input', props: { placeholder: '配置分组' } } },
    ],
  },
};

const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });

const [ConfigFormModal, configFormApi] = useVbenModal({ connectedComponent: ConfigForm });

function handleAdd() {
  configFormApi.open();
}

function handleEdit(row: ConfigApi.ConfigVO) {
  ConfigFormApi.setData({ record: row });
  ConfigFormApi.open();
}

async function handleDelete(row: ConfigApi.ConfigVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.configKey}」吗？`, '删除确认', { type: 'warning' });
    await deleteConfigApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="系统配置">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
    </Grid>
    <ConfigFormModal @success="gridApi.query()" />
  </Page>
</template>
