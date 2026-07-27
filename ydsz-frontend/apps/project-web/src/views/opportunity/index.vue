<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteOpportunityApi, getOpportunityPageApi, type OpportunityApi } from '#/api/opportunity';
import OpportunityForm from './opportunity-form.vue';
defineOptions({ name: 'OpportunityManagement' });
const gridOptions: VxeGridProps<OpportunityApi.OpportunityVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'opportunityName', title: '商机名称', width: 200 },
    { field: 'customerName', title: '客户名称', width: 150 },
    { field: 'opportunityType', title: '商机类型', width: 120 },
    { field: 'estimatedAmount', title: '预计金额', width: 120 },
    { field: 'stage', title: '阶段', width: 100 },
    { field: 'salesPerson', title: '销售人员', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getOpportunityPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'opportunityName', title: 'opportunityName', itemRender: { name: 'Input', props: { placeholder: 'opportunityName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [OpportunityFormModal, opportunityFormApi] = useVbenModal({ connectedComponent: OpportunityForm });
function handleAdd() { opportunityFormApi.open(); }
function handleEdit(row: OpportunityApi.OpportunityVO) { opportunityFormApi.setData({ record: row }); opportunityFormApi.open(); }
async function handleDelete(row: OpportunityApi.OpportunityVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.opportunityName}」吗？`, '删除确认', { type: 'warning' });
    await deleteOpportunityApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="商机管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <OpportunityFormModal @success="gridApi.query()" />
  </Page>
</template>
