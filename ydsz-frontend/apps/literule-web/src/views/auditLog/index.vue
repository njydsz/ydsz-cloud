<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteAuditLogApi, getAuditLogPageApi, type AuditLogApi } from '#/api/auditLog';
import AuditLogForm from './auditLog-form.vue';
defineOptions({ name: 'AuditLogManagement' });
const gridOptions: VxeGridProps<AuditLogApi.AuditLogVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'ruleCode', title: '规则编码', width: 150 },
    { field: 'ruleName', title: '规则名称', width: 200 },
    { field: 'triggerTime', title: '触发时间', width: 160 },
    { field: 'result', title: '结果', width: 100 },
    { field: 'duration', title: '耗时(ms)', width: 100 },
    { field: 'operator', title: '操作人', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getAuditLogPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'ruleCode', title: 'ruleCode', itemRender: { name: 'Input', props: { placeholder: 'ruleCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [AuditLogFormModal, auditLogFormApi] = useVbenModal({ connectedComponent: AuditLogForm });
function handleAdd() { auditLogFormApi.open(); }
function handleEdit(row: AuditLogApi.AuditLogVO) { auditLogFormApi.setData({ record: row }); auditLogFormApi.open(); }
async function handleDelete(row: AuditLogApi.AuditLogVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.ruleCode}」吗？`, '删除确认', { type: 'warning' });
    await deleteAuditLogApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="审计日志">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <AuditLogFormModal @success="gridApi.query()" />
  </Page>
</template>
