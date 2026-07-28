<!--
 * apps 列表/管理页面组件
 *
 * @path apps\cronjob-web\src\views\job\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 定时任务（列表页）
 * <p>定时任务（{@code ydsz_job}）的列表/分页查询页，支持 Cron 表达式配置、启停、手动触发。
 * <p>使用 VxeGrid 表格展示任务名称、Cron、负责人、最近执行时间、下次执行时间。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteJobApi, getJobPageApi, type JobApi } from '#/api/job';
import JobForm from './job-form.vue';
defineOptions({ name: 'JobManagement' });
const gridOptions: VxeGridProps<JobApi.JobVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'jobName', title: '任务名称', width: 200 },
    { field: 'jobGroup', title: '分组', width: 100 },
    { field: 'cronExpression', title: 'Cron', width: 150 },
    { field: 'jobType', title: '类型', width: 100 },
    { field: 'executorHandler', title: '执行器', width: 150 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getJobPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'jobName', title: 'jobName', itemRender: { name: 'Input', props: { placeholder: 'jobName' } } },
      { field: 'jobGroup', title: 'jobGroup', itemRender: { name: 'Input', props: { placeholder: 'jobGroup' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [JobFormModal, jobFormApi] = useVbenModal({ connectedComponent: JobForm });
function handleAdd() { jobFormApi.open(); }
function handleEdit(row: JobApi.JobVO) { jobFormApi.setData({ record: row }); jobFormApi.open(); }
async function handleDelete(row: JobApi.JobVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.jobName}」吗？`, '删除确认', { type: 'warning' });
    await deleteJobApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="任务管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <JobFormModal @success="gridApi.query()" />
  </Page>
</template>
