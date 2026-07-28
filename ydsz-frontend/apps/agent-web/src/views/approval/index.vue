<script lang="ts" setup>
/**
 * Agent 审批（列表页）
 * <p>Agent 工具调用的人工审批列表页，展示待我审批的工单。
 * <p>支持通过、驳回、转办等操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteApprovalApi, getApprovalPageApi, type ApprovalApi } from '#/api/approval';
import ApprovalForm from './approval-form.vue';
defineOptions({ name: 'ApprovalManagement' });
const gridOptions: VxeGridProps<ApprovalApi.ApprovalVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'agentId', title: 'Agent ID', width: 150 },
    { field: 'requestType', title: '请求类型', width: 120 },
    { field: 'approver', title: '审批人', width: 100 },
    { field: 'approvalStatus', title: '状态', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getApprovalPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'agentId', title: 'agentId', itemRender: { name: 'Input', props: { placeholder: 'agentId' } } },
      { field: 'approvalStatus', title: 'approvalStatus', itemRender: { name: 'Input', props: { placeholder: 'approvalStatus' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [ApprovalFormModal, approvalFormApi] = useVbenModal({ connectedComponent: ApprovalForm });
function handleAdd() { approvalFormApi.open(); }
function handleEdit(row: ApprovalApi.ApprovalVO) { approvalFormApi.setData({ record: row }); approvalFormApi.open(); }
async function handleDelete(row: ApprovalApi.ApprovalVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.agentId}」吗？`, '删除确认', { type: 'warning' });
    await deleteApprovalApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="人工审批">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <ApprovalFormModal @success="gridApi.query()" />
  </Page>
</template>
