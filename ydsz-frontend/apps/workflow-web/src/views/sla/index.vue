<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteSlaApi, getSlaPageApi, type SlaApi } from '#/api/sla';
import SlaForm from './sla-form.vue';
defineOptions({ name: 'SlaManagement' });
const gridOptions: VxeGridProps<SlaApi.SlaVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'slaName', title: 'SLA名称', width: 200 },
    { field: 'templateId', title: '模板ID', width: 150 },
    { field: 'maxDuration', title: '最大时长', width: 100 },
    { field: 'warnThreshold', title: '预警阈值', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getSlaPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'slaName', title: 'slaName', itemRender: { name: 'Input', props: { placeholder: 'slaName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [SlaFormModal, slaFormApi] = useVbenModal({ connectedComponent: SlaForm });
function handleAdd() { slaFormApi.open(); }
function handleEdit(row: SlaApi.SlaVO) { slaFormApi.setData({ record: row }); slaFormApi.open(); }
async function handleDelete(row: SlaApi.SlaVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.slaName}」吗？`, '删除确认', { type: 'warning' });
    await deleteSlaApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="SLA管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <SlaFormModal @success="gridApi.query()" />
  </Page>
</template>
