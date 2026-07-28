<script lang="ts" setup>
/**
 * 任务分组（列表页）
 * <p>任务分组的列表页，按业务域/部门/优先级对任务分组。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteJobGroupApi, getJobGroupPageApi, type JobGroupApi } from '#/api/jobGroup';
import JobGroupForm from './jobGroup-form.vue';
defineOptions({ name: 'JobGroupManagement' });
const gridOptions: VxeGridProps<JobGroupApi.JobGroupVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'groupName', title: '分组名称', width: 200 },
    { field: 'appname', title: 'AppName', width: 150 },
    { field: 'addressList', title: '地址列表', width: 250 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getJobGroupPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'groupName', title: 'groupName', itemRender: { name: 'Input', props: { placeholder: 'groupName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [JobGroupFormModal, jobGroupFormApi] = useVbenModal({ connectedComponent: JobGroupForm });
function handleAdd() { jobGroupFormApi.open(); }
function handleEdit(row: JobGroupApi.JobGroupVO) { jobGroupFormApi.setData({ record: row }); jobGroupFormApi.open(); }
async function handleDelete(row: JobGroupApi.JobGroupVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.groupName}」吗？`, '删除确认', { type: 'warning' });
    await deleteJobGroupApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="任务分组">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <JobGroupFormModal @success="gridApi.query()" />
  </Page>
</template>
