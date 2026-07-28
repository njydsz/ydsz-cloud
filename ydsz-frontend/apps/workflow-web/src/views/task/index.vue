<script lang="ts" setup>
/**
 * 流程任务（列表页）
 * <p>流程任务（{@code ydsz_flow_run_task}）的列表页，展示待办/已办。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteTaskApi, getTaskPageApi, type TaskApi } from '#/api/task';
import TaskForm from './task-form.vue';
defineOptions({ name: 'TaskManagement' });
const gridOptions: VxeGridProps<TaskApi.TaskVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'taskName', title: '任务名称', width: 200 },
    { field: 'processInstanceId', title: '实例ID', width: 150 },
    { field: 'assignee', title: '审批人', width: 100 },
    { field: 'dueDate', title: '截止日期', width: 120 },
    { field: 'status', title: '状态', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getTaskPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'taskName', title: 'taskName', itemRender: { name: 'Input', props: { placeholder: 'taskName' } } },
      { field: 'assignee', title: 'assignee', itemRender: { name: 'Input', props: { placeholder: 'assignee' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [TaskFormModal, taskFormApi] = useVbenModal({ connectedComponent: TaskForm });
function handleAdd() { taskFormApi.open(); }
function handleEdit(row: TaskApi.TaskVO) { taskFormApi.setData({ record: row }); taskFormApi.open(); }
async function handleDelete(row: TaskApi.TaskVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.taskName}」吗？`, '删除确认', { type: 'warning' });
    await deleteTaskApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="待办任务">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <TaskFormModal @success="gridApi.query()" />
  </Page>
</template>
