<!--
 * 任务连接器（列表页）
 *
 * @path apps\cronjob-web\src\views\connector\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 任务连接器（列表页）
 * <p>任务执行器的列表页，注册 HTTP/Shell/SQL/Java/Python 等执行器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteConnectorApi, getConnectorPageApi, type ConnectorApi } from '#/api/connector';
import ConnectorForm from './connector-form.vue';
defineOptions({ name: 'ConnectorManagement' });
const gridOptions: VxeGridProps<ConnectorApi.ConnectorVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'connectorName', title: '连接器名称', width: 200 },
    { field: 'connectorType', title: '类型', width: 100 },
    { field: 'endpoint', title: '端点', width: 250 },
    { field: 'authType', title: '认证', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getConnectorPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'connectorName', title: 'connectorName', itemRender: { name: 'Input', props: { placeholder: 'connectorName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [ConnectorFormModal, connectorFormApi] = useVbenModal({ connectedComponent: ConnectorForm });
function handleAdd() { connectorFormApi.open(); }
function handleEdit(row: ConnectorApi.ConnectorVO) { connectorFormApi.setData({ record: row }); connectorFormApi.open(); }
async function handleDelete(row: ConnectorApi.ConnectorVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.connectorName}」吗？`, '删除确认', { type: 'warning' });
    await deleteConnectorApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="连接器管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <ConnectorFormModal @success="gridApi.query()" />
  </Page>
</template>
