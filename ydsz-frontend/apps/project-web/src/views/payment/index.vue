<script lang="ts" setup>
/**
 * 项目付款（列表页）
 * <p>项目付款（{@code ydsz_project_payment}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deletePaymentApi, getPaymentPageApi, type PaymentApi } from '#/api/payment';
import PaymentForm from './payment-form.vue';
defineOptions({ name: 'PaymentManagement' });
const gridOptions: VxeGridProps<PaymentApi.PaymentVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'paymentAmount', title: '回款金额', width: 120 },
    { field: 'paymentDate', title: '回款日期', width: 120 },
    { field: 'paymentMethod', title: '回款方式', width: 100 },
    { field: 'description', title: '描述', width: 200 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getPaymentPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      // 无搜索项
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [PaymentFormModal, paymentFormApi] = useVbenModal({ connectedComponent: PaymentForm });
function handleAdd() { paymentFormApi.open(); }
function handleEdit(row: PaymentApi.PaymentVO) { paymentFormApi.setData({ record: row }); paymentFormApi.open(); }
async function handleDelete(row: PaymentApi.PaymentVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.paymentAmount}」吗？`, '删除确认', { type: 'warning' });
    await deletePaymentApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="回款管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <PaymentFormModal @success="gridApi.query()" />
  </Page>
</template>
