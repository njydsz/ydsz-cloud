<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteDefinitionApi, getDefinitionPageApi, type DefinitionApi } from '#/api/definition';
import DefinitionForm from './definition-form.vue';
defineOptions({ name: 'DefinitionManagement' });
const gridOptions: VxeGridProps<DefinitionApi.DefinitionVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'defName', title: '定义名称', width: 200 },
    { field: 'defCode', title: '编码', width: 150 },
    { field: 'agentType', title: '类型', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getDefinitionPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'defName', title: 'defName', itemRender: { name: 'Input', props: { placeholder: 'defName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [DefinitionFormModal, definitionFormApi] = useVbenModal({ connectedComponent: DefinitionForm });
function handleAdd() { definitionFormApi.open(); }
function handleEdit(row: DefinitionApi.DefinitionVO) { definitionFormApi.setData({ record: row }); definitionFormApi.open(); }
async function handleDelete(row: DefinitionApi.DefinitionVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.defName}」吗？`, '删除确认', { type: 'warning' });
    await deleteDefinitionApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="Agent定义">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <DefinitionFormModal @success="gridApi.query()" />
  </Page>
</template>
