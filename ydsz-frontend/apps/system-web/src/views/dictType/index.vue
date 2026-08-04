<!--
 * 字典类型管理页面 — 字典类型的分页列表、搜索、新增、编辑、删除
 *
 * @path apps\system-web\src\views\dictType\index.vue
 * @author ydsz-team
 * @since 1.1.0
-->
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

import { ElButton, ElMessage, ElMessageBox, h } from 'element-plus';

import { $t } from '#/locales';
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
    { type: 'seq', width: 50, title: $t('page.common.seq') },
    { field: 'typeCode', title: $t('business.typeCode'), width: 150 },
    { field: 'typeName', title: $t('business.typeName'), width: 150 },
    { field: 'remark', title: $t('business.description'), width: 200 },
    { field: 'status', title: $t('business.status'), width: 80 },
    { field: 'createTime', title: $t('business.createTime'), width: 160 },
    {
      field: 'action',
      title: $t('business.action'),
      width: 160,
      fixed: 'right',
      slots: {
        default: ({ row }) => {
          return h('div', { class: 'flex gap-1' }, [
            h(ElButton, { size: 'small', link: true, type: 'primary', onClick: () => handleEdit(row) }, () => $t('business.edit')),
            h(ElButton, { size: 'small', link: true, type: 'danger', onClick: () => handleDelete(row) }, () => $t('business.delete')),
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
      { field: 'typeName', title: $t('business.typeName'), itemRender: { name: 'Input', props: { placeholder: $t('business.typeName') } } },
      { field: 'typeCode', title: $t('business.typeCode'), itemRender: { name: 'Input', props: { placeholder: $t('business.typeCode') } } },
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
    await ElMessageBox.confirm($t('business.confirmDeleteName', [row.typeName]), $t('page.common.deleteConfirm'), { type: 'warning' });
    await deleteDicttypeApi(row.id);
    ElMessage.success($t('business.operationSuccess'));
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid :table-title="$t('page.dictType')">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">{{ $t('business.create') }}</ElButton>
      </template>
    </Grid>
    <DicttypeFormModal @success="gridApi.query()" />
  </Page>
</template>
