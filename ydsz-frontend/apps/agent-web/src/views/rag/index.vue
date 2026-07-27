<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteRagApi, getRagPageApi, type RagApi } from '#/api/rag';
import RagForm from './rag-form.vue';
defineOptions({ name: 'RagManagement' });
const gridOptions: VxeGridProps<RagApi.RagVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'knowledgeName', title: '知识库名称', width: 200 },
    { field: 'sourceType', title: '类型', width: 100 },
    { field: 'sourcePath', title: '路径', width: 250 },
    { field: 'chunkSize', title: '分块大小', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getRagPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'knowledgeName', title: 'knowledgeName', itemRender: { name: 'Input', props: { placeholder: 'knowledgeName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [RagFormModal, ragFormApi] = useVbenModal({ connectedComponent: RagForm });
function handleAdd() { ragFormApi.open(); }
function handleEdit(row: RagApi.RagVO) { ragFormApi.setData({ record: row }); ragFormApi.open(); }
async function handleDelete(row: RagApi.RagVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.knowledgeName}」吗？`, '删除确认', { type: 'warning' });
    await deleteRagApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="RAG知识库">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <RagFormModal @success="gridApi.query()" />
  </Page>
</template>
