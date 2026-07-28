<!--
 * 项目费用（列表页）
 *
 * @path apps\project-web\src\views\expense\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目费用（列表页）
 * <p>项目费用（{@code ydsz_project_expense}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteExpenseApi, getExpensePageApi, type ExpenseApi } from '#/api/expense';
import ExpenseForm from './expense-form.vue';
defineOptions({ name: 'ExpenseManagement' });
const gridOptions: VxeGridProps<ExpenseApi.ExpenseVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'expenseType', title: '费用类型', width: 120 },
    { field: 'amount', title: '金额', width: 120 },
    { field: 'expenseDate', title: '费用日期', width: 120 },
    { field: 'applicant', title: '申请人', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getExpensePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'expenseType', title: 'expenseType', itemRender: { name: 'Input', props: { placeholder: 'expenseType' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [ExpenseFormModal, expenseFormApi] = useVbenModal({ connectedComponent: ExpenseForm });
function handleAdd() { expenseFormApi.open(); }
function handleEdit(row: ExpenseApi.ExpenseVO) { expenseFormApi.setData({ record: row }); expenseFormApi.open(); }
async function handleDelete(row: ExpenseApi.ExpenseVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.expenseType}」吗？`, '删除确认', { type: 'warning' });
    await deleteExpenseApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="费用管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <ExpenseFormModal @success="gridApi.query()" />
  </Page>
</template>
