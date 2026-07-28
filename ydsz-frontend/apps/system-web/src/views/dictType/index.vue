<script lang="ts" setup>
/**
 * 字典类型（列表页）
 * <p>字典类型（{@code ydsz_dict_type}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  deleteDicttypeApi,
  getDicttypePageApi,
  type DicttypeApi,
} from '#/api/dictType';

import DicttypeForm from './dictType-form.vue';

defineOptions({ name: 'DicttypeManagement' });

const gridOptions: VxeGridProps<DicttypeApi.DicttypeVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'typeCode', title: '类型编码', width: 150 },
    { field: 'typeName', title: '类型名称', width: 150 },
    { field: 'remark', title: '备注', width: 200 },
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
        return await getDicttypePageApi({
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
      { field: 'typeName', title: '类型名称', itemRender: { name: 'Input', props: { placeholder: '类型名称' } } },
      { field: 'typeCode', title: '类型编码', itemRender: { name: 'Input', props: { placeholder: '类型编码' } } },
    ],
  },
};

const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });

const [DicttypeFormModal, dictTypeFormApi] = useVbenModal({ connectedComponent: DicttypeForm });

function handleAdd() {
  dictTypeFormApi.open();
}

function handleEdit(row: DicttypeApi.DicttypeVO) {
  DicttypeFormApi.setData({ record: row });
  DicttypeFormApi.open();
}

async function handleDelete(row: DicttypeApi.DicttypeVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.typeName}」吗？`, '删除确认', { type: 'warning' });
    await deleteDicttypeApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="字典类型">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
    </Grid>
    <DicttypeFormModal @success="gridApi.query()" />
  </Page>
</template>
