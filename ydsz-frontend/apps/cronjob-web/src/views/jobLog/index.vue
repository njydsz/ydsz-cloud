<script lang="ts" setup>
/**
 * 任务执行日志（列表页）
 * <p>任务执行日志的查询页，记录每次调度的开始/结束时间、状态、返回值、异常堆栈。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteJobLogApi, getJobLogPageApi, type JobLogApi } from '#/api/jobLog';
import JobLogForm from './jobLog-form.vue';
defineOptions({ name: 'JobLogManagement' });
const gridOptions: VxeGridProps<JobLogApi.JobLogVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'jobName', title: '任务名称', width: 200 },
    { field: 'jobGroup', title: '分组', width: 100 },
    { field: 'triggerTime', title: '触发时间', width: 160 },
    { field: 'triggerCode', title: '触发状态', width: 100 },
    { field: 'handleTime', title: '执行时间', width: 160 },
    { field: 'handleCode', title: '执行状态', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getJobLogPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'jobName', title: 'jobName', itemRender: { name: 'Input', props: { placeholder: 'jobName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [JobLogFormModal, jobLogFormApi] = useVbenModal({ connectedComponent: JobLogForm });
function handleAdd() { jobLogFormApi.open(); }
function handleEdit(row: JobLogApi.JobLogVO) { jobLogFormApi.setData({ record: row }); jobLogFormApi.open(); }
async function handleDelete(row: JobLogApi.JobLogVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.jobName}」吗？`, '删除确认', { type: 'warning' });
    await deleteJobLogApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="执行日志">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <JobLogFormModal @success="gridApi.query()" />
  </Page>
</template>
