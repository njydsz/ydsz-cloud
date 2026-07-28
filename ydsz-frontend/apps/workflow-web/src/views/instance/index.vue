<!--
 * apps 列表/管理页面组件
 *
 * @path apps\workflow-web\src\views\instance\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程实例（列表页）
 * <p>流程实例（{@code ydsz_flow_instance}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteInstanceApi, getInstancePageApi, type InstanceApi } from '#/api/instance';
import InstanceForm from './instance-form.vue';
defineOptions({ name: 'InstanceManagement' });
const gridOptions: VxeGridProps<InstanceApi.InstanceVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'processInstanceId', title: '实例ID', width: 150 },
    { field: 'templateName', title: '模板名称', width: 150 },
    { field: 'starter', title: '发起人', width: 100 },
    { field: 'currentTask', title: '当前节点', width: 120 },
    { field: 'currentAssignee', title: '审批人', width: 100 },
    { field: 'status', title: '状态', width: 100 },
    { field: 'startTime', title: '开始时间', width: 160 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getInstancePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'processInstanceId', title: 'processInstanceId', itemRender: { name: 'Input', props: { placeholder: 'processInstanceId' } } },
      { field: 'status', title: 'status', itemRender: { name: 'Input', props: { placeholder: 'status' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [InstanceFormModal, instanceFormApi] = useVbenModal({ connectedComponent: InstanceForm });
function handleAdd() { instanceFormApi.open(); }
function handleEdit(row: InstanceApi.InstanceVO) { instanceFormApi.setData({ record: row }); instanceFormApi.open(); }
async function handleDelete(row: InstanceApi.InstanceVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.processInstanceId}」吗？`, '删除确认', { type: 'warning' });
    await deleteInstanceApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="流程实例">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <InstanceFormModal @success="gridApi.query()" />
  </Page>
</template>
