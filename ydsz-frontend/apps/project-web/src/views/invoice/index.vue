<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteInvoiceApi, getInvoicePageApi, type InvoiceApi } from '#/api/invoice';
import InvoiceForm from './invoice-form.vue';
defineOptions({ name: 'InvoiceManagement' });
const gridOptions: VxeGridProps<InvoiceApi.InvoiceVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'invoiceCode', title: '发票编号', width: 150 },
    { field: 'customerName', title: '客户名称', width: 150 },
    { field: 'invoiceAmount', title: '发票金额', width: 120 },
    { field: 'invoiceDate', title: '开票日期', width: 120 },
    { field: 'invoiceType', title: '发票类型', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getInvoicePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'invoiceCode', title: 'invoiceCode', itemRender: { name: 'Input', props: { placeholder: 'invoiceCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [InvoiceFormModal, invoiceFormApi] = useVbenModal({ connectedComponent: InvoiceForm });
function handleAdd() { invoiceFormApi.open(); }
function handleEdit(row: InvoiceApi.InvoiceVO) { invoiceFormApi.setData({ record: row }); invoiceFormApi.open(); }
async function handleDelete(row: InvoiceApi.InvoiceVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.invoiceCode}」吗？`, '删除确认', { type: 'warning' });
    await deleteInvoiceApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="发票管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <InvoiceFormModal @success="gridApi.query()" />
  </Page>
</template>
