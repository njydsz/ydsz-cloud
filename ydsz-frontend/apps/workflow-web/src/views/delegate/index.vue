<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteDelegateApi, getDelegatePageApi, type DelegateApi } from '#/api/delegate';
import DelegateForm from './delegate-form.vue';
defineOptions({ name: 'DelegateManagement' });
const gridOptions: VxeGridProps<DelegateApi.DelegateVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'assignee', title: '委派人', width: 100 },
    { field: 'delegateTo', title: '被委派人', width: 100 },
    { field: 'startDate', title: '开始日期', width: 120 },
    { field: 'endDate', title: '结束日期', width: 120 },
    { field: 'status', title: '状态', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getDelegatePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'assignee', title: 'assignee', itemRender: { name: 'Input', props: { placeholder: 'assignee' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [DelegateFormModal, delegateFormApi] = useVbenModal({ connectedComponent: DelegateForm });
function handleAdd() { delegateFormApi.open(); }
function handleEdit(row: DelegateApi.DelegateVO) { delegateFormApi.setData({ record: row }); delegateFormApi.open(); }
async function handleDelete(row: DelegateApi.DelegateVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.assignee}」吗？`, '删除确认', { type: 'warning' });
    await deleteDelegateApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="委派管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <DelegateFormModal @success="gridApi.query()" />
  </Page>
</template>
