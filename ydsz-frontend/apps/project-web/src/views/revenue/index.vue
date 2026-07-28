<!--
 * apps 列表/管理页面组件
 *
 * @path apps\project-web\src\views\revenue\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目回款（列表页）
 * <p>项目回款（{@code ydsz_project_revenue}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteRevenueApi, getRevenuePageApi, type RevenueApi } from '#/api/revenue';
import RevenueForm from './revenue-form.vue';
defineOptions({ name: 'RevenueManagement' });
const gridOptions: VxeGridProps<RevenueApi.RevenueVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'revenueType', title: '收入类型', width: 120 },
    { field: 'amount', title: '金额', width: 120 },
    { field: 'revenueDate', title: '收入日期', width: 120 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getRevenuePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'revenueType', title: 'revenueType', itemRender: { name: 'Input', props: { placeholder: 'revenueType' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [RevenueFormModal, revenueFormApi] = useVbenModal({ connectedComponent: RevenueForm });
function handleAdd() { revenueFormApi.open(); }
function handleEdit(row: RevenueApi.RevenueVO) { revenueFormApi.setData({ record: row }); revenueFormApi.open(); }
async function handleDelete(row: RevenueApi.RevenueVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.revenueType}」吗？`, '删除确认', { type: 'warning' });
    await deleteRevenueApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="收入管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <RevenueFormModal @success="gridApi.query()" />
  </Page>
</template>
