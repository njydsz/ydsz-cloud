<!--
 * apps 列表/管理页面组件
 *
 * @path apps\cronjob-web\src\views\jobDag\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 任务 DAG（列表页）
 * <p>任务 DAG 编排的列表页，支持多任务依赖、串并行执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteJobDagApi, getJobDagPageApi, type JobDagApi } from '#/api/jobDag';
import JobDagForm from './jobDag-form.vue';
defineOptions({ name: 'JobDagManagement' });
const gridOptions: VxeGridProps<JobDagApi.JobDagVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'dagName', title: 'DAG名称', width: 200 },
    { field: 'dagCode', title: 'DAG编码', width: 150 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getJobDagPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'dagName', title: 'dagName', itemRender: { name: 'Input', props: { placeholder: 'dagName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [JobDagFormModal, jobDagFormApi] = useVbenModal({ connectedComponent: JobDagForm });
function handleAdd() { jobDagFormApi.open(); }
function handleEdit(row: JobDagApi.JobDagVO) { jobDagFormApi.setData({ record: row }); jobDagFormApi.open(); }
async function handleDelete(row: JobDagApi.JobDagVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.dagName}」吗？`, '删除确认', { type: 'warning' });
    await deleteJobDagApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="DAG管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <JobDagFormModal @success="gridApi.query()" />
  </Page>
</template>
