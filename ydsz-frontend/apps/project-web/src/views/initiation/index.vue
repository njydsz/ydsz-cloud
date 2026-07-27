<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteInitiationApi, getInitiationPageApi, type InitiationApi } from '#/api/initiation';
import InitiationForm from './initiation-form.vue';
defineOptions({ name: 'InitiationManagement' });
const gridOptions: VxeGridProps<InitiationApi.InitiationVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'projectCode', title: '项目编号', width: 150 },
    { field: 'projectName', title: '项目名称', width: 200 },
    { field: 'projectManager', title: '项目经理', width: 100 },
    { field: 'projectType', title: '项目类型', width: 100 },
    { field: 'totalBudget', title: '总预算', width: 120 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getInitiationPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'projectName', title: 'projectName', itemRender: { name: 'Input', props: { placeholder: 'projectName' } } },
      { field: 'projectCode', title: 'projectCode', itemRender: { name: 'Input', props: { placeholder: 'projectCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [InitiationFormModal, initiationFormApi] = useVbenModal({ connectedComponent: InitiationForm });
function handleAdd() { initiationFormApi.open(); }
function handleEdit(row: InitiationApi.InitiationVO) { initiationFormApi.setData({ record: row }); initiationFormApi.open(); }
async function handleDelete(row: InitiationApi.InitiationVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.projectName}」吗？`, '删除确认', { type: 'warning' });
    await deleteInitiationApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="项目立项">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <InitiationFormModal @success="gridApi.query()" />
  </Page>
</template>
