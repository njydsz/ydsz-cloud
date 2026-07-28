<!--
 * apps 列表/管理页面组件
 *
 * @path apps\system-web\src\views\dictItem\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 字典项（列表页）
 * <p>字典项（{@code ydsz_dict_item}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';

import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';

import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import {
  deleteDictitemApi,
  getDictitemPageApi,
  type DictitemApi,
} from '#/api/dictItem';

import DictitemForm from './dictItem-form.vue';

defineOptions({ name: 'DictitemManagement' });

const gridOptions: VxeGridProps<DictitemApi.DictitemVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'typeCode', title: '字典类型', width: 120 },
    { field: 'itemCode', title: '字典项编码', width: 120 },
    { field: 'itemText', title: '显示文本', width: 150 },
    { field: 'itemValue', title: '字典值', width: 120 },
    { field: 'sort', title: '排序', width: 80 },
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
        return await getDictitemPageApi({
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
      { field: 'typeCode', title: '字典类型', itemRender: { name: 'Input', props: { placeholder: '字典类型' } } },
      { field: 'itemCode', title: '字典项编码', itemRender: { name: 'Input', props: { placeholder: '字典项编码' } } },
    ],
  },
};

const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });

const [DictitemFormModal, dictItemFormApi] = useVbenModal({ connectedComponent: DictitemForm });

function handleAdd() {
  dictItemFormApi.open();
}

function handleEdit(row: DictitemApi.DictitemVO) {
  DictitemFormApi.setData({ record: row });
  DictitemFormApi.open();
}

async function handleDelete(row: DictitemApi.DictitemVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.itemCode}」吗？`, '删除确认', { type: 'warning' });
    await deleteDictitemApi(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="字典项">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
    </Grid>
    <DictitemFormModal @success="gridApi.query()" />
  </Page>
</template>
