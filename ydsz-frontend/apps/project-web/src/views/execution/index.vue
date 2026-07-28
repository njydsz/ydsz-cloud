<script lang="ts" setup>
/**
 * 项目执行（列表页）
 * <p>项目执行（{@code ydsz_project_execution}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteExecutionApi, getExecutionPageApi, type ExecutionApi } from '#/api/execution';
import ExecutionForm from './execution-form.vue';
defineOptions({ name: 'ExecutionManagement' });
const gridOptions: VxeGridProps<ExecutionApi.ExecutionVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'taskName', title: '任务名称', width: 200 },
    { field: 'assignee', title: '负责人', width: 100 },
    { field: 'plannedStart', title: '计划开始', width: 120 },
    { field: 'plannedEnd', title: '计划结束', width: 120 },
    { field: 'progress', title: '进度', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getExecutionPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'taskName', title: 'taskName', itemRender: { name: 'Input', props: { placeholder: 'taskName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [ExecutionFormModal, executionFormApi] = useVbenModal({ connectedComponent: ExecutionForm });
function handleAdd() { executionFormApi.open(); }
function handleEdit(row: ExecutionApi.ExecutionVO) { executionFormApi.setData({ record: row }); executionFormApi.open(); }
async function handleDelete(row: ExecutionApi.ExecutionVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.taskName}」吗？`, '删除确认', { type: 'warning' });
    await deleteExecutionApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="执行管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <ExecutionFormModal @success="gridApi.query()" />
  </Page>
</template>
