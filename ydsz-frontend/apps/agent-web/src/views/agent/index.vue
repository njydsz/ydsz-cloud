<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteAgentApi, getAgentPageApi, type AgentApi } from '#/api/agent';
import AgentForm from './agent-form.vue';
defineOptions({ name: 'AgentManagement' });
const gridOptions: VxeGridProps<AgentApi.AgentVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'agentName', title: 'Agent名称', width: 200 },
    { field: 'agentType', title: '类型', width: 100 },
    { field: 'modelProvider', title: '模型提供商', width: 120 },
    { field: 'modelName', title: '模型名称', width: 120 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getAgentPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'agentName', title: 'agentName', itemRender: { name: 'Input', props: { placeholder: 'agentName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [AgentFormModal, agentFormApi] = useVbenModal({ connectedComponent: AgentForm });
function handleAdd() { agentFormApi.open(); }
function handleEdit(row: AgentApi.AgentVO) { agentFormApi.setData({ record: row }); agentFormApi.open(); }
async function handleDelete(row: AgentApi.AgentVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.agentName}」吗？`, '删除确认', { type: 'warning' });
    await deleteAgentApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="Agent管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <AgentFormModal @success="gridApi.query()" />
  </Page>
</template>
