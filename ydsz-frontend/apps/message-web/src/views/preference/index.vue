<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deletePreferenceApi, getPreferencePageApi, type PreferenceApi } from '#/api/preference';
import PreferenceForm from './preference-form.vue';
defineOptions({ name: 'PreferenceManagement' });
const gridOptions: VxeGridProps<PreferenceApi.PreferenceVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'userId', title: '用户ID', width: 150 },
    { field: 'channel', title: '通道', width: 100 },
    { field: 'dndEnabled', title: '免打扰', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getPreferencePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'userId', title: 'userId', itemRender: { name: 'Input', props: { placeholder: 'userId' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [PreferenceFormModal, preferenceFormApi] = useVbenModal({ connectedComponent: PreferenceForm });
function handleAdd() { preferenceFormApi.open(); }
function handleEdit(row: PreferenceApi.PreferenceVO) { preferenceFormApi.setData({ record: row }); preferenceFormApi.open(); }
async function handleDelete(row: PreferenceApi.PreferenceVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.userId}」吗？`, '删除确认', { type: 'warning' });
    await deletePreferenceApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="消息偏好">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <PreferenceFormModal @success="gridApi.query()" />
  </Page>
</template>
