<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLanguageApi,
  getLanguagePageApi,
  type LanguageApi,
} from '#/api/language';

import LanguageForm from './language-form.vue';

defineOptions({ name: 'LanguageManagement' });

const gridOptions: VxeGridProps<LanguageApi.LanguageVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'languageCode', title: '语言编码', width: 120 },
    { field: 'languageName', title: '语言名称', width: 150 },
    { field: 'nativeName', title: '本地名称', width: 150 },
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
        return await getLanguagePageApi({
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
      { field: 'languageName', title: '语言名称', itemRender: { name: 'Input', props: { placeholder: '语言名称' } } },
      { field: 'languageCode', title: '语言编码', itemRender: { name: 'Input', props: { placeholder: '语言编码' } } },
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

const [LanguageFormModal, languageFormApi] = useVbenModal({ connectedComponent: LanguageForm });

function handleAdd() {
  languageFormApi.open();
}

function handleEdit(row: LanguageApi.LanguageVO) {
  languageFormApi.setData({ record: row });
  languageFormApi.open();
}

async function handleDelete(row: LanguageApi.LanguageVO) {
  try {
    await ElMessageBox.confirm(`确定删除语言「${row.languageName}」吗？`, '删除确认', { type: 'warning' });
    await deleteLanguageApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="语言管理">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增语言</ElButton>
      </template>
    </Grid>
    <LanguageFormModal @success="gridApi.query()" />
  </Page>
</template>
