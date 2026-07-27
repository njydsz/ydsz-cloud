<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteBatchApi, getBatchPageApi, type BatchApi } from '#/api/batch';
import BatchForm from './batch-form.vue';
defineOptions({ name: 'BatchManagement' });
const gridOptions: VxeGridProps<BatchApi.BatchVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'batchName', title: '批次名称', width: 200 },
    { field: 'channel', title: '通道', width: 100 },
    { field: 'totalcount', title: '总数', width: 80 },
    { field: 'successCount', title: '成功', width: 80 },
    { field: 'failCount', title: '失败', width: 80 },
    { field: 'status', title: '状态', width: 100 },
    { field: 'createTime', title: '创建时间', width: 160 },
    {
      field: 'action', title: '操作', width: 160, fixed: 'right',
      slots: { default: ({ row }) => h('div', { class: 'flex gap-1' }, [
        h(ElButton, { size: 'small', link: true, type: 'primary', onClick: () => handleEdit(row) }, () => '编辑'),
        h(ElButton, { size: 'small', link: true, type: 'danger', onClick: () => handleDelete(row) }, () => '删除'),
      ]) },
    },
  ],
  height: 'auto',
  pagerConfig: { pageSize: 20, pageSizes: [10, 20, 50, 100] },
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getBatchPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'batchName', title: 'batchName', itemRender: { name: 'Input', props: { placeholder: 'batchName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [BatchFormModal, batchFormApi] = useVbenModal({ connectedComponent: BatchForm });
function handleAdd() { batchFormApi.open(); }
function handleEdit(row: BatchApi.BatchVO) { batchFormApi.setData({ record: row }); batchFormApi.open(); }
async function handleDelete(row: BatchApi.BatchVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.batchName}」吗？`, '删除确认', { type: 'warning' });
    await deleteBatchApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="批量发送">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <BatchFormModal @success="gridApi.query()" />
  </Page>
</template>
