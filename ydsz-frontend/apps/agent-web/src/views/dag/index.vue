<!--
 * apps 列表/管理页面组件
 *
 * @path apps\agent-web\src\views\dag\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * Agent DAG 编排（列表页）
 * <p>Agent 任务 DAG 编排的列表页，支持查看/编辑 DAG 图。
 * <p>DAG 节点包括 LLM 调用、工具调用、条件分支、并行子任务等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteDagApi, getDagPageApi, type DagApi } from '#/api/dag';
import DagForm from './dag-form.vue';
defineOptions({ name: 'DagManagement' });
const gridOptions: VxeGridProps<DagApi.DagVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'dagName', title: 'DAG名称', width: 200 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getDagPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'dagName', title: 'dagName', itemRender: { name: 'Input', props: { placeholder: 'dagName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [DagFormModal, dagFormApi] = useVbenModal({ connectedComponent: DagForm });
function handleAdd() { dagFormApi.open(); }
function handleEdit(row: DagApi.DagVO) { dagFormApi.setData({ record: row }); dagFormApi.open(); }
async function handleDelete(row: DagApi.DagVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.dagName}」吗？`, '删除确认', { type: 'warning' });
    await deleteDagApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="DAG编排">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <DagFormModal @success="gridApi.query()" />
  </Page>
</template>
