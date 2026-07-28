<script lang="ts" setup>
/**
 * 项目预算（列表页）
 * <p>项目预算（{@code ydsz_project_budget}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteBudgetApi, getBudgetPageApi, type BudgetApi } from '#/api/budget';
import BudgetForm from './budget-form.vue';
defineOptions({ name: 'BudgetManagement' });
const gridOptions: VxeGridProps<BudgetApi.BudgetVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'budgetItemName', title: '预算项', width: 200 },
    { field: 'budgetType', title: '类型', width: 100 },
    { field: 'plannedAmount', title: '计划金额', width: 120 },
    { field: 'actualAmount', title: '实际金额', width: 120 },
    { field: 'variance', title: '差异', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getBudgetPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'budgetItemName', title: 'budgetItemName', itemRender: { name: 'Input', props: { placeholder: 'budgetItemName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [BudgetFormModal, budgetFormApi] = useVbenModal({ connectedComponent: BudgetForm });
function handleAdd() { budgetFormApi.open(); }
function handleEdit(row: BudgetApi.BudgetVO) { budgetFormApi.setData({ record: row }); budgetFormApi.open(); }
async function handleDelete(row: BudgetApi.BudgetVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.budgetItemName}」吗？`, '删除确认', { type: 'warning' });
    await deleteBudgetApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="预算管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <BudgetFormModal @success="gridApi.query()" />
  </Page>
</template>
